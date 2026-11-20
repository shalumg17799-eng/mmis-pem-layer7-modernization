package gov.medicaid.pem.tmsis.adapter.legacy;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.tmsis.application.TmsisFile;
import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The legacy TMSISOUT file (PRVTMS01 WS-TMSIS-DETAIL, 120 bytes, ADR-005) through the MAM-42 codec:
 * bytes 1-5 the record type PRV01 (the first FILLER of the layout, VALUE 'PRV01'), 6-14 the
 * Medicaid ID, 15-24 the NPI, 25 the status, 26-30 the YYDDD period, 31-120 spaces. No header or
 * trailer; records side by side with no line ends (fixed-length records, as the legacy dataset).
 */
@Component
public class TmsisOutFile implements TmsisFile {

  static final String RECORD_TYPE = "PRV01";

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("TMSISOUT");

  @Override
  public byte[] write(List<TmsisRecord> records) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (TmsisRecord t : records) {
      FixedRecord r = codec.newRecord();
      r.set("FILLER", RECORD_TYPE);
      r.set("WS-T-PROVID", t.medicaidId());
      r.set("WS-T-NPI", t.npi());
      r.set("WS-T-STATUS", t.status());
      r.set("WS-T-PERIOD-YYDDD", t.periodYyddd());
      out.writeBytes(codec.write(r));
    }
    return out.toByteArray();
  }
}
