package gov.medicaid.pem.notification.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the notice list (contract Notice): what was sent to whom, and whether it went out. The
 * contract's sentAt is a string that cannot be null, so a notice that was never sent has no sentAt
 * member.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoticeView(
    UUID noticeId, String npi, String template, String channel, String status, Instant sentAt) {}
