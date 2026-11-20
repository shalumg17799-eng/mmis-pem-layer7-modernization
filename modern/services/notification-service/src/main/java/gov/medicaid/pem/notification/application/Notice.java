package gov.medicaid.pem.notification.application;

import java.time.Instant;
import java.util.UUID;

/** One sent notice (notice table): kept for the notice list and the audit trail. */
public record Notice(
    UUID noticeId,
    String npi,
    String templateCode,
    String recipient,
    UUID eventId,
    Instant sentAt) {}
