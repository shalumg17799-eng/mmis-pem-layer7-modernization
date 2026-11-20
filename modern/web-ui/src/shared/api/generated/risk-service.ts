// GENERATED from modern/contracts/openapi/risk-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/risk-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the current risk-tier rule table
         * @description Roles: ANALYST, SUPERVISOR, ADMIN. Legacy / decisions: PRVRSK01 EVALUATE; Q-005.
         */
        get: operations["getRiskRules"];
        /**
         * Replace the risk-tier rule table with a new version
         * @description Roles: ADMIN. Legacy / decisions: data-driven table (pem-domain section 3).
         */
        put: operations["replaceRiskRules"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/risk-assessments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Compute the risk tier for a taxonomy (no side effect)
         * @description Roles: ANALYST, SUPERVISOR, SYSTEM. Legacy / decisions: PRVRSK01 CLASSIFY-ONE; D-002.
         */
        post: operations["assessRisk"];
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
        RiskAssessment: {
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi?: string;
            taxonomy: string;
            /** @enum {string} */
            tier: "L" | "M" | "H";
            ruleSetVersion: number;
            /** Format: date-time */
            assessedAt?: string;
        };
        RiskAssessmentRequest: {
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi?: string;
            taxonomy: string;
        };
        RiskRule: {
            taxonomyPrefix: string;
            /** @enum {string} */
            tier: "L" | "M" | "H";
        };
        /** @description Legacy table: 332, 251 High; 341, 291, 225 Moderate; otherwise Limited (data, not code) */
        RiskRuleSet: {
            version: number;
            /** Format: date */
            effectiveFrom?: string;
            /** @enum {string} */
            defaultTier: "L" | "M" | "H";
            rules: components["schemas"]["RiskRule"][];
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
    getRiskRules: {
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
                    "application/json": components["schemas"]["RiskRuleSet"];
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
    replaceRiskRules: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RiskRuleSet"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RiskRuleSet"];
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
    assessRisk: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RiskAssessmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RiskAssessment"];
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
}
