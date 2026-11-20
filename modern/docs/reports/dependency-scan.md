# Dependency vulnerability scan (MAM-84 AC-5)

Generated 2026-11-20 16:02 by `modern/tools/checks/scan_dependencies.py` from the OSV database (https://osv.dev); do not edit by hand.
Stands in for the OWASP dependency check and npm audit named in the AC (neither is installed here). Only open-source
package names and versions are sent; the project's own modules are not.

- Java run time: 107 third-party libraries inside `infra/local-runtime/build/libs/pem-local-runtime.jar` (built 2026-11-19 19:01)
- Web UI: 314 packages of `web-ui/package-lock.json` (25 run time, 289 build time)
- Findings: 33 (CRITICAL 8, HIGH 11, MODERATE 13, LOW 1)
- HIGH, CRITICAL or unscored findings without a written justification: **19**

Jars not looked up (13): the project's own modules and jars that are not in the Gradle module cache: api-gateway-plain.jar, directory-service-plain.jar, enrollment-service-plain.jar, fixed-width.jar, notification-service-plain.jar, provider-service-plain.jar, revalidation-service-plain.jar, risk-service-plain.jar, sanctions-service-plain.jar, screening-service-plain.jar, service-kit.jar, spring-boot-jarmode-tools-3.5.16.jar, tmsis-service-plain.jar

| Severity | Ecosystem | Package | Version | Use | Id | Basis | Summary | Fixed in | Justification |
|---|---|---|---|---|---|---|---|---|---|
| CRITICAL | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-9xv2-5v5q-p794 / CVE-2026-65905 | CVSS 9.8 | Apache Tomcat's DIGEST authenticator has an Authentication Bypass by Capture-replay vulnerability | 10.1.58, 11.0.25, 9.0.121 |  |
| CRITICAL | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-f525-44xv-f2qj / CVE-2026-65637 | CVSS 9.8 | Apache Tomcat - HTTP/2 no-authority bypass of strict SNI validation | 10.1.59, 11.0.25, 9.0.121 |  |
| CRITICAL | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-gcx9-497g-6cp6 / CVE-2026-65182 | CVSS 9.1 | Apache Tomcat has an Improper Access Control, Incorrect Authorization vulnerability | 10.1.58, 11.0.25, 9.0.121 |  |
| CRITICAL | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-h3x4-894j-xpx5 / CVE-2026-68525 | CVSS 9.1 | Apache Tomcat's FORM authentication process has an Incorrect Authorization vulnerability | 10.1.58, 11.0.25, 9.0.121 |  |
| CRITICAL | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-mqg3-r7h5-24x4 / CVE-2026-55276 | CVSS 9.1 | Apache Tomcat has an Always-Incorrect Control Flow Implementation vulnerability | 10.1.56, 11.0.23, 9.0.119 |  |
| CRITICAL | Maven | org.bouncycastle:bcprov-jdk18on | 1.80.2 | run time | GHSA-9pwp-9qqc-pr26 / CVE-2026-8763 | CVSS 9.1 | Bouncy Castle: Name Constraints bypass via trailing dot in rfc822Name and URI | 1.85 |  |
| CRITICAL | Maven | org.springframework:spring-webmvc | 6.2.19 | run time | GHSA-j9f9-w8pj-32f8 / CVE-2026-47890 | CVSS 9.8 | Spring Framework Server Sent Event stream corruption while rendering fragments | 7.0.9 |  |
| CRITICAL | Maven | org.springframework:spring-webmvc | 6.2.19 | run time | GHSA-pc63-qcmh-9cmg / CVE-2026-47884 | CVSS 9.8 | Spring Framework Improper Path Limitation in XsltView | 7.0.9 |  |
| HIGH | Maven | at.yawk.lz4:lz4-java | 1.10.1 | run time | GHSA-mcr4-qmvw-px4g / CVE-2026-106451 | advisory | yawkat LZ4 Java: Native library extraction to a shared temporary directory is vulnerable to file replacement b | 1.11.4 |  |
| HIGH | Maven | com.fasterxml.jackson.core:jackson-core | 2.21.4 | run time | GHSA-7hhh-6rmp-j9qf / CVE-2026-89425 | CVSS 7.5 | jackson-core: UTF8DataInputJsonParser._reportInvalidToken() missing maxErrorTokenLength limit -> unbounded Str | 2.18.11, 2.21.7, 2.22.3 |  |
| HIGH | Maven | com.fasterxml.jackson.core:jackson-core | 2.21.4 | run time | GHSA-p6pp-m3f8-5c89 / CVE-2026-89407 | CVSS 7.5 |  jackson-core: ReDoS: quadratic backtracking in NumberInput.PATTERN_FLOAT via looksLikeValidNumber() | 2.18.11, 2.21.7, 2.22.3 |  |
| HIGH | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-cxp5-3px4-pw24 / CVE-2026-91777 | CVSS 7.5 | jackson-databind quadratic forward-reference completion  | 2.18.11, 2.21.7, 2.22.3 |  |
| HIGH | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-q4xh-88c3-wmh7 / CVE-2026-68497 | CVSS 7.5 | jackson-databind: Duration XMLGregorianCalendar Unbounded Number Parse DoS | 2.18.10, 2.21.6, 2.22.2 |  |
| HIGH | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-wv8q-qhhj-9h54 / CVE-2026-91776 | CVSS 7.5 | jackson-databind retains every unknown raw type ID  | 2.18.11, 2.21.7, 2.22.3 |  |
| HIGH | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-h792-v28v-ppgr / CVE-2026-53404 | CVSS 7.3 | Apache Tomcat's rewrite valve has an Always-Incorrect Control Flow Implementation vulnerability | 10.1.56, 11.0.23, 9.0.119 |  |
| HIGH | Maven | org.bouncycastle:bcprov-jdk18on | 1.80.2 | run time | GHSA-qp49-qgx5-5m26 / CVE-2026-13506 | CVSS 7.5 | Bouncy Castle: Lazy ASN.1 sequence forcing resets nesting-depth guard | 1.85 |  |
| HIGH | Maven | org.postgresql:postgresql | 42.7.11 | run time | GHSA-j92g-9f8w-j867 / CVE-2026-54291 | advisory | PostgreSQL JDBC Driver: Silent channel-binding authentication downgrade via unsupported certificate algorithms | 42.7.12 |  |
| HIGH | Maven | org.xerial.snappy:snappy-java | 1.1.10.5 | run time | GHSA-gwq9-9ffj-mvrv / CVE-2026-93451 | CVSS 7.5 | snappy-java: Heap buffer overflow in Snappy.uncompress*Array typed-array methods | 1.1.10.9 |  |
| HIGH | Maven | org.xerial.snappy:snappy-java | 1.1.10.5 | run time | GHSA-q233-g92q-qxp6 / CVE-2026-108106 | CVSS 7.5 | snappy-java: Unbounded memory allocation from attacker-declared uncompressed lengtha | 1.1.10.9 |  |
| MODERATE | Maven | at.yawk.lz4:lz4-java | 1.10.1 | run time | GHSA-4v53-57pg-c464 / CVE-2026-106452 | CVSS 5.3 | yawkat LZ4 Java: LZ4BlockInputStream allocates an unvalidated compressed length from the stream header | 1.11.2 |  |
| MODERATE | Maven | at.yawk.lz4:lz4-java | 1.10.1 | run time | GHSA-6cx8-rjf8-pr8g / CVE-2026-106453 | CVSS 5.3 | yawkat LZ4 Java: LZ4DecompressorWithLength allocates the unvalidated size from the 4-byte length header, so a  | 1.11.2 |  |
| MODERATE | Maven | at.yawk.lz4:lz4-java | 1.10.1 | run time | GHSA-gm45-99xc-r7wv / CVE-2026-106450 | CVSS 5.3 | yawkat LZ4 Java: LZ4FrameInputStream reallocates block buffers for every frame, allowing CPU and GC amplificat | 1.11.4 |  |
| MODERATE | Maven | at.yawk.lz4:lz4-java | 1.10.1 | run time | GHSA-xx22-p4ch-683r / CVE-2026-59949 | CVSS 6.5 | LZ4 Java: Native XXHash implementations can crash the JVM when passed invalid byte array ranges | 1.11.1 |  |
| MODERATE | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-5gvw-p9qm-jgwh / CVE-2026-59889 | CVSS 6.5 | jackson-databind: @JsonView bypassed for @JsonUnwrapped container properties on deserialization | 2.18.9, 2.21.5, 2.22.1 |  |
| MODERATE | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-5jmj-h7xm-6q6v / CVE-2026-54515 | CVSS 5.3 | jackson-databind has case-insensitive deserialization bypasses per-property @JsonIgnoreProperties | 2.18.9, 2.21.5, 2.22.1, 3.1.4 |  |
| MODERATE | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-gx83-3vf8-gh7j / CVE-2026-83557 | CVSS 5.6 | jackson-databind: Comparable missing from DefaultBaseTypeLimitingValidator's unsafe base types (incomplete Pol | 2.18.10, 2.21.6, 2.22.2 |  |
| MODERATE | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-mhm7-754m-9p8w | CVSS 6.5 | jackson-databind: `@JsonView` bypass for creator properties with `@JsonTypeInfo(include=As.EXTERNAL_PROPERTY)` | 2.18.9, 2.21.5 |  |
| MODERATE | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-vvgp-rfg2-7rr6 / CVE-2026-77310 | CVSS 5.3 | jackson-databind: Incomplete fix for CVE-2026-54514: eager DNS resolution (SSRF) still present in InetAddress  | 2.18.9, 2.21.5, 2.22.1 |  |
| MODERATE | Maven | com.fasterxml.jackson.core:jackson-databind | 2.21.4 | run time | GHSA-wjgm-6hv5-3cvf / CVE-2026-19032 | CVSS 5.3 | jackson-databind: Path Deserialization Missing Scheme Allowlist for FileSystemProvider Resolution | 2.18.10, 2.21.6, 2.22.2 |  |
| MODERATE | Maven | org.apache.logging.log4j:log4j-api | 2.24.3 | run time | GHSA-qv9r-c865-cp47 / CVE-2026-49844 | advisory | Apache Log4j API: Improper encoding of non-finite floating-point values during MapMessage JSON serialization | 2.25.5, 2.26.1 |  |
| MODERATE | Maven | org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 | run time | GHSA-5v3h-fjv2-54fg / CVE-2026-55956 | CVSS 6.5 | Apache Tomcat has an Improper Authorization vulnerability | 10.1.56, 11.0.23, 9.0.119 |  |
| MODERATE | Maven | org.xerial.snappy:snappy-java | 1.1.10.5 | run time | GHSA-xqrx-qh46-34m7 / CVE-2026-116590 | CVSS 5.9 | snappy-java: JVM crash from unchecked ranges in Snappy.arrayCopy and SnappyOutputStream.rawWrite | 1.1.10.9 |  |
| LOW | Maven | at.yawk.lz4:lz4-java | 1.10.1 | run time | GHSA-343h-94h5-c4wr / CVE-2026-106449 | CVSS 3.7 | yawkat LZ4 Java: LZ4BlockInputStream with stopOnEmptyBlock=false recurses once per empty block, causing StackO | 1.11.4 |  |
