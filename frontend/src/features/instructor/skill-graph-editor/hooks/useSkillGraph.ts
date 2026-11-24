import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../../auth/hooks/useAuth';
import { ApiError } from '../../../../shared/types/apiTypes';
import { getOwnedSkillGraph } from '../api/skillGraphApi';
import type { OwnedSkillGraph } from '../api/skillGraphApi';

export interface OwnedSkillGraphRead {
    /** The decoded graph of the current course and session, or null. */
    graph: OwnedSkillGraph | null;
    loading: boolean;
    error: unknown;
    /** A fresh read, for "Try again" and for a stale copy (a skill referenced elsewhere is missing). */
    reload: () => void;
}

/**
 * Read-only `getOwnedSkillGraph` state (CT05.representation; DESIGN.md D12-3). The composing owner
 * (the route, and later the course editor shell) holds it and passes the graph to the views that
 * need it (CT16.final-state F1); there is no shared cache. A course or session change discards the
 * previous graph before the new read starts. The S20 editing state is FE18's and is not part of this hook.
 */
export function useOwnedSkillGraph(courseId: string): OwnedSkillGraphRead {
    const auth = useAuth(), generation = auth.generation, authenticated = auth.isAuthenticated, isCurrent = auth.isCurrent;
    const identity = authenticated ? `${generation}|${courseId}` : null;
    const [revision, setRevision] = useState(0);
    const [state, setState] = useState<{ identity: string | null; graph: OwnedSkillGraph | null; error: unknown; loading: boolean }>({ identity: null, graph: null, error: null, loading: false });
    useEffect(() => {
        if (identity === null) return;
        const controller = new AbortController();
        let live = true;
        const current = () => live && isCurrent(generation);
        setState((previous) => ({ identity, graph: previous.identity === identity ? previous.graph : null, error: null, loading: true }));
        getOwnedSkillGraph(courseId, { signal: controller.signal, isCurrent: current }).then(
            (graph) => { if (current()) setState({ identity, graph, error: null, loading: false }); },
            (error: unknown) => { if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) setState({ identity, graph: null, error, loading: false }); });
        return () => { live = false; controller.abort(); };
    }, [identity, courseId, generation, isCurrent, revision]);
    const reload = useCallback(() => setRevision((value) => value + 1), []);
    const matches = identity !== null && state.identity === identity;
    return { graph: matches ? state.graph : null, loading: identity !== null && (!matches || state.loading), error: matches ? state.error : null, reload };
}
