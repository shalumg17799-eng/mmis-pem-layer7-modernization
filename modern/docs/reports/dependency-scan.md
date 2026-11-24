# Dependency vulnerability scan (MAM-84 AC-5)

Generated 2026-11-24 13:12 by `modern/tools/checks/scan_dependencies.py` from the OSV database (https://osv.dev); do not edit by hand.
Stands in for the OWASP dependency check and npm audit named in the AC (neither is installed here). Only open-source
package names and versions are sent; the project's own modules are not.

- Java run time: 110 third-party libraries inside `infra/local-runtime/build/libs/pem-local-runtime.jar` (built 2026-11-24 13:12)
- Web UI: 322 packages of `web-ui/package-lock.json` (25 run time, 297 build time)
- Findings: 2 (CRITICAL 2)
- HIGH, CRITICAL or unscored findings without a written justification: **0**

Jars not looked up (13): the project's own modules and jars that are not in the Gradle module cache: api-gateway-plain.jar, directory-service-plain.jar, enrollment-service-plain.jar, fixed-width.jar, notification-service-plain.jar, provider-service-plain.jar, revalidation-service-plain.jar, risk-service-plain.jar, sanctions-service-plain.jar, screening-service-plain.jar, service-kit.jar, spring-boot-jarmode-tools-3.5.16.jar, tmsis-service-plain.jar

| Severity | Ecosystem | Package | Version | Use | Id | Basis | Summary | Fixed in | Justification |
|---|---|---|---|---|---|---|---|---|---|
| CRITICAL | Maven | org.springframework:spring-webmvc | 6.2.19 | run time | GHSA-j9f9-w8pj-32f8 / CVE-2026-47890 | CVSS 9.8 | Spring Framework Server Sent Event stream corruption while rendering fragments | 7.0.9 | Not exploitable here: the defect needs Server-Sent Events with view fragments; no PEM service uses SSE or view rendering (REST/JSON only; no SseEmitter, text/event-stream or view resolver under services/*/src/main and libs/*/src/main, checked 2026-11-20). No fix exists in the 6.2 line (last affected 6.2.19, the version in use; fixed only in 7.0.9, which needs Spring Boot 4). Revisit with the move to Boot 4 (Q-122). |
| CRITICAL | Maven | org.springframework:spring-webmvc | 6.2.19 | run time | GHSA-pc63-qcmh-9cmg / CVE-2026-47884 | CVSS 9.8 | Spring Framework Improper Path Limitation in XsltView | 7.0.9 | Not exploitable here: the defect needs XsltView with a /** mapping that renders views; no PEM service uses XsltView or any view rendering (checked 2026-11-20). No fix exists in the 6.2 line (fixed only in 7.0.9). Revisit with the move to Boot 4 (Q-122). |
