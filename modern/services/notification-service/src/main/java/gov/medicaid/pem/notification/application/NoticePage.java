package gov.medicaid.pem.notification.application;

import java.util.List;

/** One page of the notice list (contract NoticePage). */
public record NoticePage(List<NoticeView> items, int page, int size, long total) {}
