import { ApiError, type ProblemDetail } from '../types/apiTypes';

export interface RequestOptions extends Omit<RequestInit, 'body'> {
    body?: unknown;
    token?: string | null;
}

type UnauthorizedHandler = () => void;
let onUnauthorizedCallback: UnauthorizedHandler | null = null;

export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null) => {
    onUnauthorizedCallback = handler;
};

export async function request<T>(
    endpoint: string,
    options: RequestOptions = {}
): Promise<T> {
    const { body, token, headers: customHeaders, ...rest } = options;

    const headers: Record<string, string> = {
        'Accept': 'application/json, application/problem+json',
        ...(customHeaders as Record<string, string>),
    };

    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }

    // Inject in-memory bearer token if present
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(endpoint, {
        ...rest,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    // Handle 401 Unauthorized: Trigger session clear per ADR002 / CT01
    if (response.status === 401) {
        if (onUnauthorizedCallback) {
            onUnauthorizedCallback();
        }
    }

    if (!response.ok) {
        let problem: ProblemDetail;
        try {
            problem = await response.json();
        } catch {
            problem = {
                type: 'about:blank',
                title: response.statusText || 'HTTP Error',
                status: response.status,
                detail: `Request failed with status ${response.status}`,
                instance: endpoint,
            };
        }
        throw new ApiError(problem);
    }

    // 204 No Content
    if (response.status === 204) {
        return {} as T;
    }

    return response.json() as Promise<T>;
}