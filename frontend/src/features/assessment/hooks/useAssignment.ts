import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
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
    // Only transitions write authority. Rendering acknowledges publication, never restores eligibility.
    const authority = useRef(state), presented = useRef<State | null>(null);
    const publish = useCallback((next: State) => { authority.current = next; setState(next); }, []);
    useLayoutEffect(() => {
        if (authority.current === state) presented.current = state;
    }, [state]);
    const readSequence = useRef(0);
    const current = useCallback(() => live.current && latest.current.identity === identity && latest.current.valid && auth.isCurrent(generation), [identity, generation, auth.isCurrent]);
    // Both a synchronous ref reservation and an initiation sequence are required: React state can lag an invocation.
    const load = useCallback(async (reason?: { notice: string; status?: number }) => {
        if (!valid || !current() || mutation.current) return;
        read.current?.abort();
        const controller = new AbortController(), sequence = ++readSequence.current;
        read.current = controller;
        const eligible = () => current() && read.current === controller && !controller.signal.aborted && sequence === readSequence.current;
        publish({ ...(authority.current.identity === identity ? authority.current : initial()), phase: reason ? 'checking' : 'loading', error: null, notice: reason?.notice ?? '' });
        try {
            const data = await readLearnerAssignment(courseId!, assignmentId!, { signal: controller.signal, isCurrent: eligible });
            if (!eligible()) return;
            const notice = reason?.status === 409 ? data.submission?.graded ? 'This submission has been graded and can no longer be replaced.'
                : !data.open ? 'The deadline has passed.' : 'The assignment changed. Review its current state before choosing a file again.' : reason?.notice ?? '';
            publish({ identity, phase: 'ready', data, error: null, notice, fileName: '' });
        } catch (error) {
            if (!eligible()) return;
            publish({ ...authority.current, phase: 'error', data: null, error: failure(error), notice: reason?.notice ?? authority.current.notice });
        } finally { if (read.current === controller) read.current = null; }
    }, [valid, current, identity, courseId, assignmentId, publish]);
    useEffect(() => {
        live.current = true;
        presented.current = null;
        publish(initial());
        if (valid) void load();
        return () => { live.current = false; presented.current = null; read.current?.abort(); read.current = null; ++readSequence.current; mutation.current?.abort(); mutation.current = null; };
    }, [identity, valid, load, publish]);
    const canSubmit = () => {
        const s = authority.current;
        return current() && s.identity === identity && s.phase === 'ready' && !read.current && !mutation.current && !!s.data?.open && !s.data.submission?.graded;
    };
    // Bind actions to the evidence presented by this render, including unknown-outcome notices.
    const isSelectionCurrent = () => state === authority.current && presented.current === state && canSubmit();
    const submitFile = async (file: File, confirmedSubmittedAt: string | null) => {
        if (!isSelectionCurrent()) throw cancelled();
        const existing = authority.current.data!.submission;
        if ((existing?.submittedAt ?? null) !== confirmedSubmittedAt) throw cancelled();
        validateAssignmentFile(file);
        const controller = new AbortController(); mutation.current = controller;
        // No pre-mutation read is allowed to re-establish readiness after dispatch.
        read.current?.abort(); read.current = null; ++readSequence.current;
        publish({ ...authority.current, phase: 'uploading', notice: '', fileName: file.name });
        let notice = '', status: number | undefined;
        try {
            await submitAssignmentFile(courseId!, assignmentId!, file, { signal: controller.signal, isCurrent: () => current() && mutation.current === controller });
            notice = 'File submitted.';
        } catch (error) {
            const e = failure(error); status = e.status;
            notice = e.kind === 'unknown-outcome' || e.kind === 'cancelled' || e.kind === 'network' || (e.status ?? 0) >= 500
                ? 'The upload outcome is unknown. Review the reloaded current file before uploading again.'
                : e.status === 413 ? 'The file is larger than the allowed size.'
                : e.message;
        }
        if (!current() || mutation.current !== controller) return;
        mutation.current = null;
        // The read begins after settlement, never after a timer or an older read's completion.
        await load({ notice, status });
    };
    const visible = !valid ? { ...initial(), phase: 'error' as const, error: new ApiError('validation', 'Invalid assignment link.') } : state.identity === identity ? state : initial();
    const presentedMutation = mutation.current;
    return { assignment: visible.data, phase: visible.phase, loading: visible.phase === 'loading', error: visible.error,
        notice: visible.notice, fileName: visible.fileName, canSubmit: state === authority.current && canSubmit(), isSelectionCurrent,
        refresh: () => load(), submitFile, cancelUpload: () => {
            if (current() && mutation.current === presentedMutation) presentedMutation?.abort();
        } };
}
