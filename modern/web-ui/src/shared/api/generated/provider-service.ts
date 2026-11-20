// GENERATED from modern/contracts/openapi/provider-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/providers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search providers by NPI, Medicaid ID, name or status (replaces CICS PINQ)
         * @description Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM. Legacy / decisions: PRVCICS1 inquiry; work-queue filters.
         */
        get: operations["searchProviders"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/providers/{npi}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get one provider record (all PRVRECRD fields)
         * @description Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM. Legacy / decisions: PRVCICS1 READ PRVPROVN; a PROVIDER sees only their own record.
         */
        get: operations["getProvider"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/providers/{npi}/approval": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve (ACTIVE, due date = approval + 5 years) or deny (DENIED) a pending provider whose screening passed
         * @description Roles: ANALYST, SUPERVISOR. Legacy / decisions: D-019, D-003.
         */
        post: operations["decideProvider"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/providers/{npi}/status-changes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply a sanction status change (terminate, suspend, reinstate)
         * @description Roles: SYSTEM, SUPERVISOR. Legacy / decisions: PRVSAN01 APPLY-SANCTION; D-006, D-013.
         */
        post: operations["changeProviderStatus"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/providers/{npi}/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Audit history of a provider (every state change)
         * @description Roles: SUPERVISOR, ADMIN. Legacy / decisions: new (audit log).
         */
        get: operations["getProviderAudit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/legacy/provider-master": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Export the provider master as 130-byte PRVRECRD records (Phase 9 parity)
         * @description Roles: ADMIN. Legacy / decisions: VSAM master; D-007.
         */
        get: operations["exportProviderMaster"];
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
        /** @description D-019: an analyst approval sets ACTIVE after a PASS screening. */
        ApprovalRequest: {
            /** @enum {string} */
            decision: "APPROVE" | "DENY";
            reason?: string;
        };
        AuditEntry: {
            /** Format: date-time */
            at: string;
            actor: string;
            action: string;
            before?: Record<string, never> | null;
            after?: Record<string, never> | null;
            /** Format: uuid */
            correlationId?: string;
        };
        AuditPage: {
            items: components["schemas"]["AuditEntry"][];
            page: number;
            size: number;
            total: number;
        };
        FieldError: {
            /** @enum {string} */
            code: "E101" | "E102" | "E103" | "E104" | "E201" | "E202" | "E203" | "E204" | "E205";
            field: string;
            message?: string;
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
        Provider: {
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi: string;
            medicaidId: string;
            /**
             * @description IN individual, GR group, FA facility (D-008)
             * @enum {string}
             */
            providerType: "IN" | "GR" | "FA";
            taxonomy: string;
            /** @enum {string|null} */
            riskTier?: "L" | "M" | "H" | null;
            /**
             * @description P pending, A active, T terminated, D denied, S suspended (D-006)
             * @enum {string}
             */
            enrollStatus: "P" | "A" | "T" | "D" | "S";
            /** @enum {string} */
            screenFlag: "U" | "P" | "F";
            /** Format: date */
            effectiveDate: string;
            /** Format: date */
            endDate?: string | null;
            /**
             * Format: date
             * @description Set at approval and at each completed revalidation (D-003); 4-digit years (D-004)
             */
            revalidationDueDate?: string | null;
            individual?: components["schemas"]["Individual"];
            organization?: components["schemas"]["Organization"];
            version?: number;
        };
        ProviderPage: {
            items: components["schemas"]["Provider"][];
            page: number;
            size: number;
            total: number;
        };
        StatusChangeRequest: {
            /**
             * @description Legacy SANCIN T, S, R. Applied whatever the current status (D-027, Q-006).
             * @enum {string}
             */
            action: "TERMINATE" | "SUSPEND" | "REINSTATE";
            reason?: string;
            /** Format: date */
            effectiveDate: string;
            /** Format: uuid */
            sanctionId?: string;
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
    searchProviders: {
        parameters: {
            query?: {
                /** @description Exact NPI */
                npi?: string;
                /** @description Exact Medicaid ID (the legacy alternate index) */
                medicaidId?: string;
                /** @description Name contains */
                name?: string;
                /** @description Enrolment status */
                status?: "P" | "A" | "T" | "D" | "S";
                /** @description Screen flag */
                screenFlag?: "U" | "P" | "F";
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
                    "application/json": components["schemas"]["ProviderPage"];
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
    getProvider: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                npi: string;
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
                    "application/json": components["schemas"]["Provider"];
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
    decideProvider: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                npi: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ApprovalRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Provider"];
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
    changeProviderStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                npi: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StatusChangeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Provider"];
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
        };
    };
    getProviderAudit: {
        parameters: {
            query?: {
                /** @description Page number (0-based) */
                page?: number;
                /** @description Page size */
                size?: number;
            };
            header?: never;
            path: {
                npi: string;
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
                    "application/json": components["schemas"]["AuditPage"];
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
    exportProviderMaster: {
        parameters: {
            query?: never;
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
        };
    };
}
