// GENERATED from modern/contracts/openapi/directory-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/directory/providers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Public search of active providers
         * @description Roles: PUBLIC. Legacy / decisions: PRVDIR01; Q-008.
         */
        get: operations["searchDirectory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/fhir/Practitioner": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * FHIR R4 Practitioner search (individual providers)
         * @description Roles: PUBLIC. Legacy / decisions: ADR-006.
         */
        get: operations["searchPractitioners"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/fhir/Organization": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * FHIR R4 Organization search (groups and facilities)
         * @description Roles: PUBLIC. Legacy / decisions: ADR-006.
         */
        get: operations["searchOrganizations"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/fhir/PractitionerRole": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * FHIR R4 PractitionerRole search (specialty = taxonomy)
         * @description Roles: PUBLIC. Legacy / decisions: ADR-006.
         */
        get: operations["searchPractitionerRoles"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/directory-extracts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Produce the nightly MCO directory file for a business date
         * @description Roles: ADMIN, SYSTEM. Legacy / decisions: PRVDIR01.
         */
        post: operations["startDirectoryExtract"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/directory-extracts/{runId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a directory extract run with its status, counts and failure reason
         * @description Roles: ADMIN. Legacy / decisions: PRVDIR01 counters (MAM-67). Added in 1.1.0 (user decision 2026-11-19): run status for the batch jobs page (MAM-93).
         */
        get: operations["getDirectoryExtract"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/batch/directory-extracts/{runId}/file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download the DIROUT file of a run (100-byte records)
         * @description Roles: ADMIN. Legacy / decisions: DIROUT; D-028.
         */
        get: operations["getDirectoryExtractFile"];
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
        DirectoryEntry: {
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi: string;
            medicaidId?: string;
            /** @enum {string} */
            providerType: "IN" | "GR" | "FA";
            /** @enum {string|null} */
            riskTier?: "L" | "M" | "H" | null;
            /** @description Composed from the name fields (D-028) */
            displayName: string;
            taxonomy?: string;
        };
        DirectoryPage: {
            items: components["schemas"]["DirectoryEntry"][];
            page: number;
            size: number;
            total: number;
        };
        /** @description FHIR R4 Bundle (searchset) of Plan-Net style resources */
        FhirBundle: {
            /** @constant */
            resourceType: "Bundle";
            /** @constant */
            type?: "searchset";
            total?: number;
            entry?: Record<string, never>[];
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
    searchDirectory: {
        parameters: {
            query?: {
                /** @description Name contains */
                name?: string;
                /** @description Type */
                providerType?: "IN" | "GR" | "FA";
                /** @description Taxonomy */
                taxonomy?: string;
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
                    "application/json": components["schemas"]["DirectoryPage"];
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
        };
    };
    searchPractitioners: {
        parameters: {
            query?: {
                /** @description NPI as system|value */
                identifier?: string;
                /** @description Name */
                name?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description FHIR searchset Bundle */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/fhir+json": components["schemas"]["FhirBundle"];
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
        };
    };
    searchOrganizations: {
        parameters: {
            query?: {
                /** @description NPI as system|value */
                identifier?: string;
                /** @description Name */
                name?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description FHIR searchset Bundle */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/fhir+json": components["schemas"]["FhirBundle"];
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
        };
    };
    searchPractitionerRoles: {
        parameters: {
            query?: {
                /** @description Taxonomy code */
                specialty?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description FHIR searchset Bundle */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/fhir+json": components["schemas"]["FhirBundle"];
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
        };
    };
    startDirectoryExtract: {
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
    getDirectoryExtract: {
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
    getDirectoryExtractFile: {
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
