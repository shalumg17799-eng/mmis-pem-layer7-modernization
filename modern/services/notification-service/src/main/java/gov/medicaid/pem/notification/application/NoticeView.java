package gov.medicaid.pem.notification.application;

import java.time.Instant;
import java.util.UUID;

/** One row of the notice list (contract Notice): what was sent to whom, and whether it went out. */
public record NoticeView(
    UUID noticeId, String npi, String template, String channel, String status, Instant sentAt) {}
