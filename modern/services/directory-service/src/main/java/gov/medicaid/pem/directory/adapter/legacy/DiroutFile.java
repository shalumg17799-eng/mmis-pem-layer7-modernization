package gov.medicaid.pem.directory.adapter.legacy;

import gov.medicaid.pem.directory.application.DirectoryEntry;
import gov.medicaid.pem.directory.application.DirectoryFile;
import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The legacy DIROUT file (PRVDIR01, 100 bytes, ADR-005) through the MAM-42 codec: bytes 1-9 the
 * Medicaid ID, 10-19 the NPI, 20-21 the type, 22 the risk tier, 23-82 the composed name (D-028),
 * 83-100 spaces. Values are right-padded and cut on the right when longer (an alphanumeric MOVE,
 * only in this legacy writer). No header or trailer; records side by side with no line ends
 * (fixed-length records, as the legacy dataset).
 */
@Component
public class DiroutFile implements DirectoryFile {

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("DIROUT");

  @Override
  public byte[] write(List<DirectoryEntry> entries) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (DirectoryEntry e : entries) {
      FixedRecord r = codec.newRecord();
      put(r, "DIR-MEDICAID-ID", e.medicaidId());
      put(r, "DIR-NPI", e.npi());
      put(r, "DIR-TYPE", e.providerType());
      put(r, "DIR-RISK-TIER", e.riskTier());
      put(r, "DIR-NAME", e.displayName());
      out.writeBytes(codec.write(r));
    }
    return out.toByteArray();
  }

  private void put(FixedRecord r, String field, String value) {
    if (value == null) {
      return;
    }
    int length = codec.layout().field(field).length();
    r.set(field, value.length() <= length ? value : value.substring(0, length));
  }
}
