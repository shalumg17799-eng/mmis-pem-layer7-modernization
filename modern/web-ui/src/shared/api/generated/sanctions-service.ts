// GENERATED from modern/contracts/openapi/sanctions-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/sanctions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Sanction history
         * @description Roles: ANALYST, SUPERVISOR, ADMIN. Legacy / decisions: D-018.
         */
        get: operations["listSanctions"];
        put?: never;
        /**
         * Record a sanction (T terminate, S suspend, R reinstate) and apply it
         * @description Roles: SUPERVISOR. Legacy / decisions: PRVSAN01; D-006, D-013, D-018, D-026, D-027.
         */
        post: operations["recordSanction"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/sanction-intake-runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Load a legacy SANCIN file (80-byte records) for a business date
         * @description Roles: ADMIN, SYSTEM. Legacy / decisions: SANCIN.
         */
        post: operations["startSanctionIntakeRun"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/sanction-intake-runs/{runId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a sanction intake run with its counts
         * @description Roles: ADMIN. Legacy / decisions: PRVSAN01 counters.
         */
        get: operations["getSanctionIntakeRun"];
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
        FieldError: {
            /** @enum {string} */
            code: "E101" | "E102" | "E103" | "E104" | "E201" | "E202" | "E203" | "E204" | "E205";
            field: string;
            message?: string;
        };
        FileRunRequest: {
            /** @description Legacy fixed-width input file */
            file: string;
            /** Format: date */
            businessDate: string;
            /** @description A real job parameter (D-017) */
            batchId?: string;
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
        Sanction: {
            /** Format: uuid */
            sanctionId: string;
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi: string;
            /** @enum {string} */
            action: "T" | "S" | "R";
            reason?: string;
            /** Format: date */
            effectiveDate: string;
            sourceFeed?: string;
            recordedBy?: string;
            /** Format: date-time */
            recordedAt: string;
            /** @enum {string} */
            resultingStatus?: "T" | "S" | "A";
        };
        SanctionPage: {
            items: components["schemas"]["Sanction"][];
            page: number;
            size: number;
            total: number;
        };
        SanctionRequest: {
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi: string;
            /** @description T, S or R; anything else is rejected (D-026) */
            action: string;
            reason?: string;
            /** Format: date */
            effectiveDate: string;
            sourceFeed?: string;
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
    listSanctions: {
        parameters: {
            query?: {
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
                    "application/json": components["schemas"]["SanctionPage"];
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
    recordSanction: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SanctionRequest"];
            };
        };
        responses: {
            /** @description OK */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Sanction"];
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
            /** @description Business validation failed */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description The status change could not be applied: provider-service unreachable or failing (MAM-52 AC-7, Q-B13, D-024). Nothing is recorded and no SanctionApplied is published. Added in 1.0.1 (Q-096). */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    startSanctionIntakeRun: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "multipart/form-data": components["schemas"]["FileRunRequest"];
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
            /** @description Business validation failed */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    getSanctionIntakeRun: {
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
}
