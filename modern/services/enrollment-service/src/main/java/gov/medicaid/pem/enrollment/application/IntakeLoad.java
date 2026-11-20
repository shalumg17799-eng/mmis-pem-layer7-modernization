package gov.medicaid.pem.enrollment.application;

import gov.medicaid.pem.enrollment.domain.ErrorCode;
import gov.medicaid.pem.fixedwidth.FieldDef;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The APPINPUT intake (MAM-56, MAM-57; PRVENR01), the work of one run's Spring Batch job (ADR-004).
 * Every 200-byte record is read in file order as the 130-byte PRVRECRD in bytes 1-130 (bytes
 * 131-200 and the tier, status, flag and dates of bytes 32-50 are not taken over) and gets the same
 * validation, storage and events as an online application, with source LEGACY_FILE, each record on
 * its own. A rejected application writes one APPERROR row; one rejection never stops the load.
 *
 * <p>Counts (each record exactly once): written = VALIDATED; skipped = a duplicate NPI (E103 only)
 * or a record that an earlier run of the same business date already processed (AC-8: nothing is
 * loaded twice, no row, no event); errored = any other rejection, an unreadable record or a record
 * that could not be stored. An unreadable record or a storage failure ends the run FAILED naming
 * the record (MAM-57 AC-5, AC-6); a run with rejections alone is COMPLETED (legacy RC 4).
 */
@Service
public class IntakeLoad {

  private static final Logger LOG = LoggerFactory.getLogger(IntakeLoad.class);
  static final int LRECL = 200;
  static final String DEFAULT_BATCH = "DEFBATCH";

  private final IntakeRuns runs;
  private final SubmitApplication submit;
  private final Clock clock;
  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("APPINPUT");

  public IntakeLoad(IntakeRuns runs, SubmitApplication submit, Clock clock) {
    this.runs = runs;
    this.submit = submit;
    this.clock = clock;
  }

  /** PRVENR01 INITIALIZE: the batch id is a real job parameter (D-017), DEFBATCH when blank. */
  public UUID open(LocalDate businessDate, String batchId) {
    String batch = batchId == null || batchId.isBlank() ? DEFAULT_BATCH : batchId.trim();
    UUID runId = UUID.randomUUID();
    runs.create(runId, businessDate, batch, clock.instant());
    LOG.info("PRVENR01: STARTING ENROLLMENT LOAD - BATCH {}", batch);
    return runId;
  }

  /** One record per line, or 200-byte blocks when the file has no line end. */
  public static List<byte[]> records(byte[] file) {
    List<byte[]> out = new ArrayList<>();
    boolean lines = false;
    for (byte b : file) {
      if (b == '\n') {
        lines = true;
        break;
      }
    }
    if (lines) {
      int start = 0;
      for (int i = 0; i <= file.length; i++) {
        if (i == file.length || file[i] == '\n') {
          int end = i;
          if (end > start && file[end - 1] == '\r') {
            end--;
          }
          if (i < file.length || end > start) {
            byte[] r = new byte[end - start];
            System.arraycopy(file, start, r, 0, r.length);
            out.add(r);
          }
          start = i + 1;
        }
      }
    } else {
      for (int i = 0; i < file.length; i += LRECL) {
        int len = Math.min(LRECL, file.length - i);
        byte[] r = new byte[len];
        System.arraycopy(file, i, r, 0, len);
        out.add(r);
      }
    }
    return out;
  }

  public void process(UUID runId, LocalDate businessDate, byte[] file, String submittedBy) {
    int read = 0;
    int written = 0;
    int errored = 0;
    int skipped = 0;
    int n = 0;
    for (byte[] raw : records(file)) {
      n++;
      read++;
      if (raw.length != LRECL) {
        errored++;
        runs.counts(runId, read, written, errored, skipped);
        throw new IllegalStateException(
            "record " + n + " is " + raw.length + " bytes, not " + LRECL + ": the file is unreadable");
      }
      String rawText = new String(raw, StandardCharsets.US_ASCII);
      if (runs.alreadyProcessed(runId, businessDate, rawText)) {
        skipped++;
        runs.counts(runId, read, written, errored, skipped);
        continue;
      }
      NewApplication app = application(rawText);
      StoredApplication stored;
      try {
        stored =
            submit.fromFile(app, submittedBy, applicationId -> runs.attach(applicationId, runId, rawText));
      } catch (RuntimeException e) {
        errored++;
        runs.counts(runId, read, written, errored, skipped);
        throw new IllegalStateException(
            "record " + n + " NPI " + app.npi() + " could not be stored: "
                + e.getMessage(),
            e);
      }
      if (SubmitApplication.VALIDATED.equals(stored.status())) {
        written++;
      } else {
        runs.addErrorRow(runId, errorRow(rawText));
        boolean duplicateOnly =
            stored.errors().stream().allMatch(e -> e.code() == ErrorCode.E103);
        if (duplicateOnly) {
          skipped++;
        } else {
          errored++;
        }
      }
      runs.counts(runId, read, written, errored, skipped);
    }
    runs.complete(runId, clock.instant());
    LOG.info(
        "PRVENR01: ENROLLMENT LOAD ENDED - READ={} WRITTEN={} ERRORED={} SKIPPED={}",
        read,
        written,
        errored,
        skipped);
  }

  public void failed(UUID runId, LocalDate businessDate, String reason) {
    LOG.error("PRVENR01 run {} for {} FAILED: {}", runId, businessDate, reason);
    runs.fail(runId, reason, clock.instant());
  }

  /** PRVRECRD bytes 1-130: the key fields and the view of the type (individual or organisation). */
  private NewApplication application(String r) {
    String type = field(r, "PRV-TYPE-CODE");
    NewApplication.Individual individual = null;
    NewApplication.Organization organization = null;
    if ("IN".equals(type)) {
      individual =
          new NewApplication.Individual(
              field(r, "PRV-LAST-NAME"),
              field(r, "PRV-FIRST-NAME"),
              field(r, "PRV-LICENSE-NO"),
              field(r, "PRV-LICENSE-STATE"));
    } else if ("GR".equals(type) || "FA".equals(type)) {
      organization =
          new NewApplication.Organization(
              field(r, "PRV-LEGAL-NAME"),
              field(r, "PRV-DBA-NAME"),
              field(r, "PRV-OWNER-NPI"));
    }
    return new NewApplication(
        field(r, "PRV-NPI"),
        field(r, "PRV-MEDICAID-ID"),
        type,
        field(r, "PRV-TAXONOMY"),
        individual,
        organization);
  }

  /**
   * One APPERROR row (PRVENR01 WRITE-ERROR-RECORD, 250 bytes): the NPI and type code as read, then
   * ' ERR:VALIDATION-FAILED', padded with spaces.
   */
  static String errorRow(String raw) {
    String row = raw.substring(0, 10) + " TYPE:" + raw.substring(19, 21) + " ERR:VALIDATION-FAILED";
    return row + " ".repeat(250 - row.length());
  }

  /** A field of the PRVRECRD layout by its position, without trailing spaces; blank is null. */
  private String field(String record, String name) {
    FieldDef f = codec.layout().field(name);
    return blankToNull(record.substring(f.start() - 1, f.start() - 1 + f.length()));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
