package gov.medicaid.pem.sanctions.application;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.fixedwidth.FixedWidthException;
import gov.medicaid.pem.kit.web.ApiException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The SANCIN intake (MAM-55; PRVSAN01 MAIN-LOGIC and PROCESS-SANCTION), the work of one run's
 * Spring Batch job (ADR-004): every 80-byte record is read in file order through the last one and
 * applied like an online sanction (RecordSanction: through provider-service first, then kept in the
 * history with source feed SANCIN and announced, each record on its own). An unknown NPI is counted
 * notFound (logged 'PRVSAN01: NPI NOT FOUND <npi>'), an unknown action or impossible date is
 * counted rejected, and a record an earlier run of the same business date already applied is
 * skipped (AC-7); the run goes on. An unreadable record or a failure to apply ends the run FAILED
 * naming the record (AC-6). Counts are kept after every record, so a failed run shows what was
 * done; the record that ends the run is not counted (it was neither applied, refused nor skipped).
 *
 * <p>The key a re-run uses to recognise an applied record is written in the transaction that keeps
 * the sanction and announces it, so a record is applied once, all or nothing (AC-6, AC-7). The
 * reason a failed run shows is short and has no SQL text when the cause is a database error
 * (D-024).
 */
@Service
public class SanctionIntake {

  private static final Logger LOG = LoggerFactory.getLogger(SanctionIntake.class);
  static final String SOURCE_FEED = "SANCIN";
  static final int LRECL = 80;
  static final int REASON_MAX = 200;

  private final SanctionIntakeRuns runs;
  private final RecordSanction record;
  private final Clock clock;
  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("SANCIN");

  public SanctionIntake(SanctionIntakeRuns runs, RecordSanction record, Clock clock) {
    this.runs = runs;
    this.record = record;
    this.clock = clock;
  }

  public UUID open(LocalDate businessDate) {
    UUID runId = UUID.randomUUID();
    runs.create(runId, businessDate, clock.instant());
    return runId;
  }

  /**
   * Splits the uploaded file into records: one per line, or 80-byte blocks when it has no line end.
   */
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

  public void process(UUID runId, LocalDate businessDate, byte[] file, String recordedBy) {
    int read = 0;
    int applied = 0;
    int notFound = 0;
    int rejected = 0;
    int n = 0;
    for (byte[] raw : records(file)) {
      n++;
      read++;
      FixedRecord r;
      try {
        r = codec.read(raw, n);
      } catch (FixedWidthException e) {
        if (raw.length != LRECL) {
          runs.counts(runId, read - 1, applied, notFound, rejected);
          throw new IllegalStateException(
              "record " + n + " is " + raw.length + " bytes, not " + LRECL + ": " + e.getMessage(),
              e);
        }
        rejected++;
        LOG.warn("PRVSAN01: RECORD {} REJECTED: {}", n, e.getMessage());
        runs.counts(runId, read, applied, notFound, rejected);
        continue;
      }
      String npi = r.get("SAN-NPI").trim();
      String action = r.get("SAN-ACTION");
      String reason = r.get("SAN-REASON");
      LocalDate date = r.getDate("SAN-EFF-DATE");
      try {
        if (runs.alreadyApplied(runId, businessDate, npi, action, reason, date)) {
          LOG.info(
              "PRVSAN01: RECORD {} NPI {} ALREADY APPLIED FOR {}: SKIPPED", n, npi, businessDate);
        } else {
          record.record(
              new SanctionCommand(npi, action, reason.isEmpty() ? null : reason, date, SOURCE_FEED),
              recordedBy,
              s -> runs.link(s.sanctionId(), runId));
          applied++;
        }
      } catch (ApiException e) {
        if (e.status() == HttpStatus.NOT_FOUND) {
          notFound++;
          LOG.warn("PRVSAN01: NPI NOT FOUND {}", npi);
        } else if (e.status() == HttpStatus.UNPROCESSABLE_ENTITY) {
          rejected++;
          LOG.warn("PRVSAN01: RECORD {} NPI {} REJECTED: {}", n, npi, e.getMessage());
        } else {
          runs.counts(runId, read - 1, applied, notFound, rejected);
          throw couldNotApply(n, npi, e);
        }
      } catch (RuntimeException e) {
        runs.counts(runId, read - 1, applied, notFound, rejected);
        throw couldNotApply(n, npi, e);
      }
      runs.counts(runId, read, applied, notFound, rejected);
    }
    runs.complete(runId, clock.instant());
    int terminated = runs.find(runId).map(SanctionIntakeRuns.Run::terminated).orElse(0);
    LOG.info("PRVSAN01: PROVIDERS TERMINATED={}", String.format("%05d", terminated));
    LOG.info(
        "PRVSAN01 run {} for {}: READ={} APPLIED={} NOT-FOUND={} REJECTED={}",
        runId,
        businessDate,
        read,
        applied,
        notFound,
        rejected);
  }

  /** A run that failed for a reason that is already known. */
  public void failed(UUID runId, LocalDate businessDate, String reason) {
    LOG.error("PRVSAN01 run {} for {} FAILED: {}", runId, businessDate, reason);
    runs.fail(runId, reason, clock.instant());
  }

  /**
   * A run that failed with an exception: the full exception is logged, the run shows its short
   * reason (D-024).
   */
  public void failed(UUID runId, LocalDate businessDate, Throwable failure) {
    String reason = reasonOf(failure);
    LOG.error("PRVSAN01 run {} for {} FAILED: {}", runId, businessDate, reason, failure);
    runs.fail(runId, reason, clock.instant());
  }

  /**
   * A step that ends a run. Its message is already the reason the run shows: the prefix and the
   * reason of the cause, which for a database error is the first line of its root cause, at most
   * {@value #REASON_MAX} characters, so no SQL statement reaches the caller (D-024).
   */
  public static final class RunFailure extends IllegalStateException {

    public RunFailure(String prefix, Throwable cause) {
      super(reasonOf(prefix, cause), cause);
    }
  }

  private static RunFailure couldNotApply(int n, String npi, RuntimeException cause) {
    return new RunFailure("record " + n + " NPI " + npi + " could not be applied: ", cause);
  }

  private static String reasonOf(Throwable failure) {
    return failure instanceof RunFailure ? failure.getMessage() : reasonOf("", failure);
  }

  private static String reasonOf(String prefix, Throwable cause) {
    if (!isDatabaseError(cause)) {
      return prefix + (cause.getMessage() == null ? cause.toString() : cause.getMessage());
    }
    Throwable root = NestedExceptionUtils.getMostSpecificCause(cause);
    String message = root.getMessage() == null ? root.toString() : root.getMessage();
    String text = prefix + message.strip().lines().findFirst().orElse(root.toString());
    return text.substring(0, Math.min(text.length(), REASON_MAX));
  }

  private static boolean isDatabaseError(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof DataAccessException || t instanceof SQLException) {
        return true;
      }
    }
    return false;
  }
}
