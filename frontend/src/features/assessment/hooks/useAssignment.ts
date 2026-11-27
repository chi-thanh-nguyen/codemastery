import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError, cancelled } from '../../../shared/types/apiTypes';
import { assignmentIdValid, readLearnerAssignment, submitAssignmentFile, validateAssignmentFile } from '../api/assignmentApi';
import type { B5LearnerAssignment } from '../api/assignmentApi';

type Phase = 'loading' | 'ready' | 'uploading' | 'checking' | 'error';
interface State { identity: string; phase: Phase; data: B5LearnerAssignment | null; error: ApiError | null; notice: string; fileName: string }
const failure = (e: unknown) => e instanceof ApiError ? e : new ApiError('network', "Couldn't load the assignment. Try again.");
export function useAssignment(courseId: string | undefined, assignmentId: string | undefined) {
    const auth = useAuth(), generation = auth.generation;
    const identity = `${generation}|${courseId}|${assignmentId}`;
    const valid = assignmentIdValid(courseId) && assignmentIdValid(assignmentId) && auth.isAuthenticated && auth.user?.role === 'Learner';
    const initial = (): State => ({ identity, phase: 'loading', data: null, error: null, notice: '', fileName: '' });
    const [state, setState] = useState<State>(initial);
    const latest = useRef({ identity, auth, valid }); latest.current = { identity, auth, valid };
    const live = useRef(false), read = useRef<AbortController | null>(null), mutation = useRef<AbortController | null>(null);
    const snapshot = useRef(state); snapshot.current = state;
    const readSequence = useRef(0);
    const current = useCallback(() => live.current && latest.current.identity === identity && latest.current.valid && auth.isCurrent(generation), [identity, generation, auth.isCurrent]);
    // Both a synchronous ref reservation and an initiation sequence are required: React state can lag an invocation.
    const load = useCallback(async (reason?: { notice: string; status?: number }) => {
        if (!valid || !current() || mutation.current) return;
        read.current?.abort();
        const controller = new AbortController(), sequence = ++readSequence.current;
        read.current = controller;
        const eligible = () => current() && read.current === controller && !controller.signal.aborted && sequence === readSequence.current;
        setState(s => ({ ...(s.identity === identity ? s : initial()), phase: reason ? 'checking' : 'loading', error: null, notice: reason?.notice ?? '' }));
        try {
            const data = await readLearnerAssignment(courseId!, assignmentId!, { signal: controller.signal, isCurrent: eligible });
            if (!eligible()) return;
            const notice = reason?.status === 409 ? data.submission?.graded ? 'This submission has been graded and can no longer be replaced.'
                : !data.open ? 'The deadline has passed.' : 'The assignment changed. Review its current state before choosing a file again.' : reason?.notice ?? '';
            setState({ identity, phase: 'ready', data, error: null, notice, fileName: '' });
        } catch (error) {
            if (!eligible()) return;
            setState(s => ({ ...s, phase: 'error', data: null, error: failure(error), notice: reason?.notice ?? s.notice }));
        } finally { if (read.current === controller) read.current = null; }
    }, [valid, current, identity, courseId, assignmentId]);
    useEffect(() => {
        live.current = true;
        setState(initial());
        if (valid) void load();
        return () => { live.current = false; read.current?.abort(); read.current = null; ++readSequence.current; mutation.current?.abort(); mutation.current = null; };
    }, [identity, valid, load]);
    const canSubmit = () => {
        const s = snapshot.current;
        return current() && s.identity === identity && s.phase === 'ready' && !read.current && !mutation.current && !!s.data?.open && !s.data.submission?.graded;
    };
    const submitFile = async (file: File, confirmedSubmittedAt: string | null) => {
        if (!canSubmit()) throw cancelled();
        const existing = snapshot.current.data!.submission;
        if ((existing?.submittedAt ?? null) !== confirmedSubmittedAt) throw cancelled();
        validateAssignmentFile(file);
        const controller = new AbortController(); mutation.current = controller;
        // No pre-mutation read is allowed to re-establish readiness after dispatch.
        read.current?.abort(); read.current = null; ++readSequence.current;
        setState(s => ({ ...s, phase: 'uploading', notice: '', fileName: file.name }));
        let notice = '', status: number | undefined;
        try {
            await submitAssignmentFile(courseId!, assignmentId!, file, { signal: controller.signal, isCurrent: () => current() && mutation.current === controller });
            notice = 'File submitted.';
        } catch (error) {
            const e = failure(error); status = e.status;
            notice = e.kind === 'unknown-outcome' || e.kind === 'cancelled' || e.kind === 'network' || (e.status ?? 0) >= 500
                ? 'The upload outcome is unknown. Review the reloaded current file before uploading again.'
                : e.status === 413 ? 'The file is larger than the allowed size.'
                : e.status === 400 ? 'The file was rejected. Choose a valid PDF or ZIP file and check its name and contents.'
                : e.status === 415 ? 'The upload format was rejected. Choose a PDF or ZIP file again.' : e.message;
        }
        if (!current() || mutation.current !== controller) return;
        mutation.current = null;
        // The read begins after settlement, never after a timer or an older read's completion.
        await load({ notice, status });
    };
    const visible = !valid ? { ...initial(), phase: 'error' as const, error: new ApiError('validation', 'Invalid assignment link.') } : state.identity === identity ? state : initial();
    return { assignment: visible.data, phase: visible.phase, loading: visible.phase === 'loading', error: visible.error,
        notice: visible.notice, fileName: visible.fileName, canSubmit: valid && state.identity === identity && canSubmit(),
        refresh: () => load(), submitFile, cancelUpload: () => mutation.current?.abort() };
}
