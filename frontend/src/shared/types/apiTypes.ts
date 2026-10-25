export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
export interface ProblemDetailError { field: string; code: string; message: string }
export interface ProblemDetail {
    type: string;
    title: string;
    status: number;
    detail: string;
    instance: string;
    errors?: ProblemDetailError[];
}
export type ApiErrorKind = 'http' | 'protocol' | 'network' | 'cancelled' | 'configuration' | 'validation';
/** Safe local representations; never raw response diagnostics. */
export class ApiError extends Error {
    constructor(
        public readonly kind: ApiErrorKind,
        message: string,
        public readonly status?: number,
        public readonly problem?: ProblemDetail,
        public readonly fields: Readonly<Record<string, string>> = {},
    ) { super(message); this.name = 'ApiError'; }
}
export const cancelled = () => new ApiError('cancelled', 'This request is no longer current.');
export const protocolError = () => new ApiError('protocol', 'The server returned an invalid response.');
export const isRecord = (value: unknown): value is Record<string, unknown> =>
    value !== null && typeof value === 'object' && !Array.isArray(value);
export function formFailure(error: unknown, allowedFields: readonly string[]) {
    if (!(error instanceof ApiError)) return { message: 'The request could not be completed.', fields: {} };
    return {
        message: error.message,
        fields: Object.fromEntries(Object.entries(error.fields).filter(([field]) => allowedFields.includes(field))),
    };
}
