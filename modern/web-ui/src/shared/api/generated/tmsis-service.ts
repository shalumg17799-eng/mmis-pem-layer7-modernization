// GENERATED from modern/contracts/openapi/tmsis-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/batch/tmsis-extracts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List T-MSIS extract runs and archive generations
         * @description Roles: ADMIN. Legacy / decisions: GDG MMPRV.TMSIS.ARCHIVE.
         */
        get: operations["listTmsisExtracts"];
        put?: never;
        /**
         * Produce the T-MSIS provider extract for a business date and archive it
         * @description Roles: ADMIN, SYSTEM. Legacy / decisions: PRVMTH01 STEP020-030; D-015, Q-007.
         */
        post: operations["startTmsisExtract"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/tmsis-extracts/{runId}/file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download the TMSISOUT file of a run (120-byte records)
         * @description Roles: ADMIN. Legacy / decisions: TMSISOUT.
         */
        get: operations["getTmsisExtractFile"];
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
        BatchRunPage: {
            items: components["schemas"]["BatchRun"][];
            page: number;
            size: number;
            total: number;
        };
        BatchRunRequest: {
            /**
             * Format: date
             * @description Explicit business date (D-020)
             */
            businessDate: string;
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
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    listTmsisExtracts: {
        parameters: {
            query?: {
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
                    "application/json": components["schemas"]["BatchRunPage"];
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
    startTmsisExtract: {
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
    getTmsisExtractFile: {
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
