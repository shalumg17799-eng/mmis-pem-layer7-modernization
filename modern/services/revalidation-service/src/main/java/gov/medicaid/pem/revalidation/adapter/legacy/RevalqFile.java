package gov.medicaid.pem.revalidation.adapter.legacy;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.revalidation.application.QueueFile;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The legacy REVALQ file (PRVREV01 REVAL-OUT-RECORD, 80 bytes, ADR-005) through the MAM-42 codec:
 * one record per opened case in ascending NPI order, REV-ACTION 'RESCREEN-REQUIRED' (PRVREV01 91),
 * records side by side with no line ends (fixed-length records, as the legacy dataset).
 */
@Component
public class RevalqFile implements QueueFile {

  static final String ACTION = "RESCREEN-REQUIRED";

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("REVALQ");

  @Override
  public byte[] write(List<RevalidationCase> opened) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    opened.stream()
        .sorted(Comparator.comparing(RevalidationCase::npi))
        .forEach(
            c -> {
              FixedRecord r = codec.newRecord();
              r.set("REV-NPI", c.npi());
              r.setDate("REV-DUE-DATE", c.dueDate());
              r.set("REV-ACTION", ACTION);
              out.writeBytes(codec.write(r));
            });
    return out.toByteArray();
  }
}
