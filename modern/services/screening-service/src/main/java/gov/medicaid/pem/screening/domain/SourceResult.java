package gov.medicaid.pem.screening.domain;

import java.time.Instant;

/**
 * What one source answered (SCRNREC: source, result P/F/C, verdict date and detail; D-005), with
 * the E2xx code of a failing source where one applies (D-016).
 */
public record SourceResult(
    Source source, String result, Instant verdictAt, String detail, String errorCode) {}
