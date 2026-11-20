package gov.medicaid.pem.sanctions.application;

import gov.medicaid.pem.sanctions.domain.Sanction;
import java.util.List;

/** One page of the sanction history (contract SanctionPage). */
public record SanctionPage(List<Sanction> items, int page, int size, long total) {}
