export interface ProblemDetailError {
    field: string;
    code: string;
    message: string;
}

export interface ProblemDetail {
    type: string;
    title: string;
    status: number;
    detail: string;
    instance: string;
    errors?: ProblemDetailError[];
    [key: string]: unknown;
}

export class ApiError extends Error {
    public readonly status: number;
    public readonly problem: ProblemDetail;

    constructor(problem: ProblemDetail) {
        super(problem.detail || problem.title || `API Error: ${problem.status}`);
        this.name = 'ApiError';
        this.status = problem.status;
        this.problem = problem;
    }
}