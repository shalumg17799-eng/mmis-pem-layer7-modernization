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
         * @description Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM. Legacy / decisions: PRVCICS1 inquiry; work-queue filters; keyset paging Q-132 (1.2.0). Every given filter must match. The items are in ascending NPI order, with or without afterNpi (MAM-37 AC-6). Two ways to page, never mixed in one call. (1) Offset paging, unchanged: page and size; a call without afterNpi answers exactly as in 1.1.0 (same results, same order, same paging). (2) Keyset paging (1.2.0): afterNpi and size; the answer holds only the providers whose NPI is greater than afterNpi, in ascending NPI order, at most size of them; page must be absent or 0 (any other page is a 400 problem) and the answer then has page 0. To walk every match: the first call has no afterNpi (page absent or 0); each next call repeats the same filters and size and sets afterNpi to the npi of the last item of the previous answer; the walk ends at the first answer whose items list is empty (an answer with fewer items than size is the last one that holds providers, so a caller may also stop there). Because the key only moves forward, a provider that matches the filters for the whole walk is returned exactly once, whatever changes elsewhere in the data during the walk. total is always the number of ALL providers that match the filters (npi, medicaidId, name, status, screenFlag), never only those after the key: it is the same on every answer of a walk while the data does not change, and a caller compares it between answers to notice a change (the Q-132 safety net).
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
        /** @description Part of after in an APPROVED or DENIED entry (MAM-40, 1.1.0): the decision as sent to decideProvider. Both members are always written; reason is null when none was sent. */
        AuditDecision: {
            /** @enum {string} */
            decision: "APPROVE" | "DENY";
            reason: string | null;
        };
        /** @description One entry of the provider history (MAM-40, D-018). actor is the user, or the producing service for an event. action is one of REGISTERED, SCREENING_VERDICT, RISK_TIER_ASSIGNED, APPROVED, DENIED, TERMINATED, SUSPENDED, REINSTATED, REVALIDATION_PASSED, NOTICE_SENT. before and after are ProviderSnapshot (members declared in 1.1.0, Q-131 A). correlationId is absent when the request or event had none that is a uuid. */
        AuditEntry: {
            /** Format: date-time */
            at: string;
            actor: string;
            action: string;
            before?: components["schemas"]["ProviderSnapshot"];
            after?: components["schemas"]["ProviderSnapshot"];
            /** Format: uuid */
            correlationId?: string;
        };
        /** @description The only member of after in a NOTICE_SENT entry (MAM-40 AC-4, D-012, 1.1.0): the notice id and template of the NoticeSent event, as received. */
        AuditNotice: {
            /** Format: uuid */
            noticeId: string;
            template: string;
        };
        AuditPage: {
            items: components["schemas"]["AuditEntry"][];
            page: number;
            size: number;
            total: number;
        };
        /** @description Part of after in a RISK_TIER_ASSIGNED entry (MAM-40, 1.1.0): the tier of the RiskTierAssigned event, as received. */
        AuditRisk: {
            /** @enum {string} */
            tier: "L" | "M" | "H";
        };
        /** @description Part of after in a SCREENING_VERDICT entry (MAM-40, 1.1.0): the result of the ScreeningCompleted event, as received. */
        AuditScreening: {
            /** @enum {string} */
            result: "PASS" | "FAIL";
        };
        /** @description Part of after in a TERMINATED, SUSPENDED or REINSTATED entry (MAM-40 AC-5, 1.1.0): the StatusChangeRequest as applied. All four members are always written; reason and sanctionId are null when none was sent. */
        AuditStatusChange: {
            /** @enum {string} */
            action: "TERMINATE" | "SUSPEND" | "REINSTATE";
            reason: string | null;
            /** Format: date */
            effectiveDate: string;
            /** Format: uuid */
            sanctionId: string | null;
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
        /** @description One answer of searchProviders. The items are in ascending NPI order (MAM-37 AC-6). */
        ProviderPage: {
            items: components["schemas"]["Provider"][];
            /** @description The page asked for; 0 with afterNpi (keyset paging, 1.2.0) */
            page: number;
            size: number;
            /** @description The number of ALL providers that match the filters, with or without afterNpi (never only those after the key, Q-132) */
            total: number;
        };
        /** @description The provider record as written to audit_log before or after one change (MAM-40; members declared in 1.1.0, Q-131 A). Null for before in REGISTERED and NOTICE_SENT entries. A snapshot of the record carries all eleven provider members, nulls included; after also carries the one part named for its action: screening (SCREENING_VERDICT), risk (RISK_TIER_ASSIGNED), decision (APPROVED, DENIED) or statusChange (TERMINATED, SUSPENDED, REINSTATED); REVALIDATION_PASSED has no part. The after of NOTICE_SENT carries only notice. No member is required because no member is in every entry. The schema itself admits null so that before and after can reference it directly. */
        ProviderSnapshot: {
            /** @description National Provider Identifier (10 digits), the provider key */
            npi?: string;
            medicaidId?: string;
            /** @enum {string} */
            providerType?: "IN" | "GR" | "FA";
            taxonomy?: string;
            /** @enum {string|null} */
            riskTier?: "L" | "M" | "H" | null;
            /** @enum {string} */
            enrollStatus?: "P" | "A" | "T" | "D" | "S";
            /** @enum {string} */
            screenFlag?: "U" | "P" | "F";
            /** Format: date */
            effectiveDate?: string;
            /** Format: date */
            endDate?: string | null;
            /** Format: date */
            revalidationDueDate?: string | null;
            version?: number;
            screening?: components["schemas"]["AuditScreening"];
            risk?: components["schemas"]["AuditRisk"];
            decision?: components["schemas"]["AuditDecision"];
            statusChange?: components["schemas"]["AuditStatusChange"];
            notice?: components["schemas"]["AuditNotice"];
        } | null;
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
                /** @description Page number (0-based). With afterNpi it must be absent or 0; any other page is a 400 problem. */
                page?: number;
                /** @description Page size (the same with or without afterNpi) */
                size?: number;
                /** @description Keyset paging (1.2.0, Q-132). Present: the answer holds only the providers whose NPI is greater than afterNpi, in ascending NPI order (10-digit NPIs compare the same as text and as numbers), at most size of them; every other filter combines with it; page must be absent or 0. The value need not be on file or match the filters: it is only a position (normally the npi of the last item of the previous answer). total still counts ALL providers that match the filters, not only those after the key. Absent: nothing changes, the answer is exactly as in 1.1.0. Strict input (lesson b of the code reviews): a value that is not exactly 10 digits (empty, shorter, longer, letters, a sign, spaces or other padding) or afterNpi given more than once is a 400 problem, never coerced and never ignored. */
                afterNpi?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK. Without afterNpi: page page of the matches in ascending NPI order. With afterNpi: up to size matches whose NPI is greater than afterNpi, in ascending NPI order, and page 0; an empty items list means there is nothing after the key (the end of a walk). In both, total is the number of all matches of the filters. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProviderPage"];
                };
            };
            /** @description Invalid request: a malformed filter or paging value (npi, medicaidId, status, screenFlag, page, size, afterNpi; afterNpi not exactly 10 digits or given more than once), or afterNpi together with a page other than 0. */
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
