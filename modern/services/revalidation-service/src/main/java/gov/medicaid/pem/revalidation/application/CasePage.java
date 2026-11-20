package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.util.List;

/** One page of revalidation cases (contract RevalidationCasePage). */
public record CasePage(List<RevalidationCase> items, int page, int size, long total) {}
