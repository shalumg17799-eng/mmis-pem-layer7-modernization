// GENERATED from modern/contracts/openapi/enrollment-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/applications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List applications (a PROVIDER sees only their own)
         * @description Roles: PROVIDER, ANALYST, SUPERVISOR. Legacy / decisions: APPERROR review.
         */
        get: operations["listApplications"];
        put?: never;
        /**
         * Submit an enrolment application (validates E101, E102, E103, E104)
         * @description Roles: PROVIDER, ANALYST. Legacy / decisions: PRVENR01 VALIDATE-APPLICATION; D-001, D-016.
         */
        post: operations["submitApplication"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/applications/{applicationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one application with its validation errors
         * @description Roles: PROVIDER, ANALYST, SUPERVISOR. Legacy / decisions: PRVENR01.
         */
        get: operations["getApplication"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/intake-runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Load a legacy APPINPUT file (200-byte records) for a business date
         * @description Roles: ADMIN, SYSTEM. Legacy / decisions: PRVDAY01 STEP010; D-017, D-020, D-025.
         */
        post: operations["startIntakeRun"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/intake-runs/{runId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get an intake run with its read, written, errored and skipped counts
         * @description Roles: ADMIN. Legacy / decisions: PRVENR01 TERMINATE-PROGRAM counters.
         */
        get: operations["getIntakeRun"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/intake-runs/{runId}/error-file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download the APPERROR file of a run (250-byte records)
         * @description Roles: ADMIN. Legacy / decisions: APPERROR; D-001 one row per invalid application.
         */
        get: operations["getIntakeErrorFile"];
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
        Application: {
            /** Format: uuid */
            applicationId: string;
            /** @enum {string} */
            status: "SUBMITTED" | "VALIDATED" | "REJECTED";
            /** @description Exactly one error per failed check (D-001) */
            errors?: components["schemas"]["FieldError"][];
            npi: string;
            medicaidId?: string;
            providerType?: string;
            taxonomy?: string;
            individual?: components["schemas"]["Individual"];
            organization?: components["schemas"]["Organization"];
            /** @enum {string} */
            source: "PORTAL" | "LEGACY_FILE";
            /** Format: date-time */
            submittedAt: string;
        };
        ApplicationPage: {
            items: components["schemas"]["Application"][];
            page: number;
            size: number;
            total: number;
        };
        ApplicationRequest: {
            /** @description Validated: present (E101), 10 digits */
            npi: string;
            medicaidId: string;
            /** @description IN, GR or FA (E102); feed codes are mapped (D-008) */
            providerType: string;
            /** @description Present (E104) */
            taxonomy: string;
            individual?: components["schemas"]["Individual"];
            organization?: components["schemas"]["Organization"];
        };
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
        /** @description PRV-INDIVIDUAL-DATA view of PRV-VARIANT (provider type IN). */
        Individual: {
            lastName?: string;
            firstName?: string;
            licenseNo?: string;
            licenseState?: string;
        };
        /** @description PRV-ORG-DATA view of PRV-VARIANT (provider type GR or FA). */
        Organization: {
            legalName?: string;
            dbaName?: string;
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            ownerNpi?: string;
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
    listApplications: {
        parameters: {
            query?: {
                /** @description Status */
                status?: "SUBMITTED" | "VALIDATED" | "REJECTED";
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
                    "application/json": components["schemas"]["ApplicationPage"];
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
    submitApplication: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ApplicationRequest"];
            };
        };
        responses: {
            /** @description OK */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Application"];
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
    getApplication: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Application id */
                applicationId: string;
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
                    "application/json": components["schemas"]["Application"];
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
    startIntakeRun: {
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
    getIntakeRun: {
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
    getIntakeErrorFile: {
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
