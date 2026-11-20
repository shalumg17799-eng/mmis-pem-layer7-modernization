// GENERATED from modern/contracts/openapi/revalidation-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/revalidations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List revalidation cases (work queue)
         * @description Roles: ANALYST, SUPERVISOR. Legacy / decisions: REVALQ; D-029.
         */
        get: operations["listRevalidationCases"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/revalidations/{caseId}/completion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Complete a revalidation case with its screening result
         * @description Roles: SYSTEM, ANALYST. Legacy / decisions: D-003.
         */
        post: operations["completeRevalidation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/revalidation-sweeps": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Run the revalidation sweep for a business date
         * @description Roles: ADMIN, SYSTEM. Legacy / decisions: PRVMTH01 STEP010.
         */
        post: operations["startRevalidationSweep"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/revalidation-sweeps/{runId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a revalidation sweep run with its status, counts and failure reason
         * @description Roles: ADMIN. Legacy / decisions: PRVREV01 counters (MAM-45, question C-8). Added in 1.1.0 (user decision 2026-11-19): run status for the batch jobs page (MAM-93).
         */
        get: operations["getRevalidationSweep"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/revalidation-sweeps/{runId}/queue-file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download the REVALQ file of a sweep (80-byte records)
         * @description Roles: ADMIN. Legacy / decisions: REVALQ.
         */
        get: operations["getRevalidationQueueFile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        BatchRun: {
            /** Format: uuid */
            runId: string;
            job: string;
            /** Format: date */
            businessDate: string;
            /** @enum {string} */
            status: "RUNNING" | "COMPLETED" | "FAILED";
            counts?: {
                [key: string]: number;
            };
            /** @description Why the run failed (D-024) */
            error?: string | null;
            /** Format: date-time */
            startedAt?: string;
            /** Format: date-time */
            endedAt?: string | null;
        };
        BatchRunRequest: {
            /**
             * Format: date
             * @description Explicit business date (D-020)
             */
            businessDate: string;
        };
        CompletionRequest: {
            /** Format: uuid */
            screeningId: string;
            /** @enum {string} */
            result: "PASS" | "FAIL";
        };
        FieldError: {
            /** @enum {string} */
            code: "E101" | "E102" | "E103" | "E104" | "E201" | "E202" | "E203" | "E204" | "E205";
            field: string;
            message?: string;
        };
        /** @description RFC 7807 problem details. `code` carries a legacy error code (E101-E205) where one applies (D-016). */
        Problem: {
            /** Format: uri */
            type?: string;
            title: string;
            status: number;
            detail?: string;
            /** @enum {string} */
            code?: "E101" | "E102" | "E103" | "E104" | "E201" | "E202" | "E203" | "E204" | "E205";
            errors?: components["schemas"]["FieldError"][];
        };
        RevalidationCase: {
            /** Format: uuid */
            caseId: string;
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi: string;
            /** Format: date */
            dueDate: string;
            /** @enum {string} */
            status: "DUE" | "IN_SCREENING" | "COMPLETED" | "CANCELLED";
            /** Format: date-time */
            openedAt?: string;
            /** Format: date-time */
            closedAt?: string | null;
        };
        RevalidationCasePage: {
            items: components["schemas"]["RevalidationCase"][];
            page: number;
            size: number;
            total: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    listRevalidationCases: {
        parameters: {
            query?: {
                /** @description Status */
                status?: "DUE" | "IN_SCREENING" | "COMPLETED" | "CANCELLED";
                /** @description NPI */
                npi?: string;
                /** @description Page number (0-based) */
                page?: number;
                /** @description Page size */
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RevalidationCasePage"];
                };
            };
            /** @description Invalid request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Not authenticated */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Role not allowed */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    completeRevalidation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Case id */
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CompletionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RevalidationCase"];
                };
            };
            /** @description Not authenticated */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Role not allowed */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Conflict with the current state */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    startRevalidationSweep: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BatchRunRequest"];
            };
        };
        responses: {
            /** @description OK */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BatchRun"];
                };
            };
            /** @description Invalid request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Not authenticated */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Role not allowed */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    getRevalidationSweep: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Run id */
                runId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BatchRun"];
                };
            };
            /** @description Not authenticated */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Role not allowed */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    getRevalidationQueueFile: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Run id */
                runId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/plain": string;
                };
            };
            /** @description Not authenticated */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Role not allowed */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
}
