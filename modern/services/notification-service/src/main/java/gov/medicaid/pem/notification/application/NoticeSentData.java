package gov.medicaid.pem.notification.application;

import java.util.UUID;

/** Data of the NoticeSent event (contracts/asyncapi/events.yaml#NoticeSentData). */
public record NoticeSentData(UUID noticeId, String npi, String template) {}
