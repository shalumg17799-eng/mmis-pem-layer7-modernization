package gov.medicaid.pem.screening.adapter.legacy;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.screening.application.ScreeningRuns;
import gov.medicaid.pem.screening.application.ScrnoutFile;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The legacy SCRNOUT file (SCRNREC, 67 bytes, ADR-005) through the MAM-42 codec: four rows per
 * screened provider, providers in ascending NPI order and rows in the order LICBOARD, OIGLEIE,
 * SAMGOV, SSADMF; NPI, SOURCE (left-justified), RESULT (P, F or C), VERDICT-DATE (the run's business
 * date, CCYYMMDD) and DETAIL cut to 40 characters. No line ends (fixed-length records).
 */
@Component
public class ScrnoutWriter implements ScrnoutFile {

  private static final List<Source> ORDER =
      List.of(Source.LICBOARD, Source.OIGLEIE, Source.SAMGOV, Source.SSADMF);

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("SCRNREC");

  @Override
  public byte[] write(List<ScreeningRuns.Screened> screened, LocalDate verdictDate) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    screened.stream()
        .sorted(Comparator.comparing(ScreeningRuns.Screened::npi))
        .forEach(
            s ->
                s.results().stream()
                    .sorted(Comparator.comparingInt(r -> ORDER.indexOf(r.source())))
                    .forEach(r -> out.writeBytes(codec.write(row(s.npi(), r, verdictDate)))));
    return out.toByteArray();
  }

  private FixedRecord row(String npi, SourceResult r, LocalDate verdictDate) {
    FixedRecord rec = codec.newRecord();
    rec.set("SCR-NPI", npi);
    rec.set("SCR-SOURCE", r.source().name());
    rec.set("SCR-RESULT", r.result());
    rec.setDate("SCR-VERDICT-DATE", verdictDate);
    String detail = r.detail() == null ? "" : r.detail();
    rec.set("SCR-DETAIL", detail.length() <= 40 ? detail : detail.substring(0, 40));
    return rec;
  }
}
