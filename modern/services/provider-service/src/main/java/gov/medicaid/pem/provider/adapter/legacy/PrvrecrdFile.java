package gov.medicaid.pem.provider.adapter.legacy;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.provider.application.ExportProviderMaster;
import gov.medicaid.pem.provider.domain.Provider;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The provider master in the legacy PRVRECRD layout (130 bytes, D-007, ADR-005) through the MAM-42
 * codec. The type code selects the REDEFINES view: IN writes the individual fields, GR and FA the
 * organisation fields (legal name, DBA name, owner NPI in bytes 121-130). Alphanumeric values are
 * right-padded with spaces and cut on the right when longer (an alphanumeric MOVE, only in this
 * legacy-format writer); an unset date is zeros and an unset risk tier a space. One record per line,
 * no header or trailer.
 */
@Component
public class PrvrecrdFile implements ExportProviderMaster.MasterFile {

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("PRVRECRD");

  @Override
  public String write(List<Provider> providers) {
    return providers.stream().map(this::record).collect(Collectors.joining("\n"));
  }

  private String record(Provider p) {
    FixedRecord r = codec.newRecord();
    put(r, "PRV-NPI", p.npi());
    put(r, "PRV-MEDICAID-ID", p.medicaidId());
    String type = p.providerType() == null ? null : p.providerType().code();
    put(r, "PRV-TYPE-CODE", type);
    put(r, "PRV-TAXONOMY", p.taxonomy());
    put(r, "PRV-RISK-TIER", p.riskTier() == null ? null : p.riskTier().code());
    put(r, "PRV-ENROLL-STATUS", p.enrollStatus() == null ? null : p.enrollStatus().code());
    put(r, "PRV-SCREEN-FLAG", p.screenFlag() == null ? null : p.screenFlag().code());
    date(r, "PRV-EFFECTIVE-DATE", p.effectiveDate());
    date(r, "PRV-REVAL-DUE-DATE", p.revalidationDueDate());
    if ("IN".equals(type)) {
      put(r, "PRV-LAST-NAME", p.lastName());
      put(r, "PRV-FIRST-NAME", p.firstName());
      put(r, "PRV-LICENSE-NO", p.licenseNo());
      put(r, "PRV-LICENSE-STATE", p.licenseState());
    } else if ("GR".equals(type) || "FA".equals(type)) {
      put(r, "PRV-LEGAL-NAME", p.legalName());
      put(r, "PRV-DBA-NAME", p.dbaName());
      put(r, "PRV-OWNER-NPI", p.ownerNpi());
    }
    return new String(codec.write(r), StandardCharsets.US_ASCII);
  }

  private void put(FixedRecord r, String field, String value) {
    if (value == null) {
      return;
    }
    int length = codec.layout().field(field).length();
    r.set(field, value.length() <= length ? value : value.substring(0, length));
  }

  private static void date(FixedRecord r, String field, LocalDate value) {
    if (value != null) {
      r.setDate(field, value);
    }
  }
}
