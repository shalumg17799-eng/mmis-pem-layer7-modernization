// GENERATED from modern/contracts/openapi/notification-service.yaml by scripts/gen-api.mjs. Do not edit.
export interface paths {
    "/api/v1/notices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List notices sent (a PROVIDER sees only their own)
         * @description Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN. Legacy / decisions: PRVNT*; D-012.
         */
        get: operations["listNotices"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notice-templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List notice templates
         * @description Roles: ADMIN. Legacy / decisions: PRVNTTRM/SUS/RST/GEN.
         */
        get: operations["listNoticeTemplates"];
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
        FieldError: {
            /** @enum {string} */
            code: "E101" | "E102" | "E103" | "E104" | "E201" | "E202" | "E203" | "E204" | "E205";
            field: string;
            message?: string;
        };
        Notice: {
            /** Format: uuid */
            noticeId: string;
            /**
             * @description National Provider Identifier (10 digits), the provider key
             * @example 1003000111
             */
            npi: string;
            /** @enum {string} */
            template: "TRM" | "SUS" | "RST" | "GEN" | "APPROVED" | "DENIED" | "REJECTED" | "REVALIDATION_DUE" | "EXTRACT_READY";
            /** @enum {string} */
            channel?: "EMAIL";
            /** @enum {string} */
            status: "SENT" | "FAILED";
            /** Format: date-time */
            sentAt?: string;
        };
        NoticePage: {
            items: components["schemas"]["Notice"][];
            page: number;
            size: number;
            total: number;
        };
        NoticeTemplate: {
            code: string;
            subject: string;
            body?: string;
        };
        NoticeTemplateList: components["schemas"]["NoticeTemplate"][];
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
    listNotices: {
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
                    "application/json": components["schemas"]["NoticePage"];
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
    listNoticeTemplates: {
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
                    "application/json": components["schemas"]["NoticeTemplateList"];
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
