package gov.medicaid.pem.sanctions.application;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * recordSanction (MAM-52), PRVSAN01 PROCESS-SANCTION and APPLY-SANCTION: the request is checked
 * (unknown action: 422, D-026), the status is changed through provider-service first (D-013), and
 * only then is the sanction kept in the history and announced as SanctionApplied, in one
 * transaction (D-012, D-018). An unknown provider is a 404 and a status change that could not be
 * applied a 503; neither keeps or announces anything (D-024, Q-B13). No check of the current status
 * (D-027).
 */
@Service
public class RecordSanction {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");
  static final int REASON_MAX = 60;
  static final int SOURCE_FEED_MAX = 20;

  private final ProviderStatusPort provider;
  private final SanctionHistory history;
  private final EventOutbox outbox;
  private final TransactionOperations tx;
  private final Clock clock;

  public RecordSanction(
      ProviderStatusPort provider,
      SanctionHistory history,
      EventOutbox outbox,
      TransactionOperations tx,
      Clock clock) {
    this.provider = provider;
    this.history = history;
    this.outbox = outbox;
    this.tx = tx;
    this.clock = clock;
  }

  public Sanction record(SanctionCommand c, String recordedBy) {
    return record(c, recordedBy, kept -> {});
  }

  /**
   * As {@link #record(SanctionCommand, String)}; {@code inTransaction} runs in the same transaction
   * as the history row and the event, after the history row is written. When it fails, neither is
   * kept (the intake writes the key of its run there, MAM-55 AC-6, AC-7).
   */
  public Sanction record(SanctionCommand c, String recordedBy, Consumer<Sanction> inTransaction) {
    SanctionAction action =
        SanctionAction.fromCode(c.action())
            .orElseThrow(
                () ->
                    unprocessable(
                        "Field action must be one of T, S or R (T terminate, S suspend, R reinstate)."));
    if (c.npi() == null || !NPI.matcher(c.npi()).matches()) {
      throw unprocessable("Field npi must be 10 digits.");
    }
    if (c.effectiveDate() == null) {
      throw unprocessable("Field effectiveDate is required.");
    }
    if (c.reason() != null && c.reason().length() > REASON_MAX) {
      throw unprocessable("Field reason must be at most " + REASON_MAX + " characters.");
    }
    if (c.sourceFeed() != null && c.sourceFeed().length() > SOURCE_FEED_MAX) {
      throw unprocessable("Field sourceFeed must be at most " + SOURCE_FEED_MAX + " characters.");
    }
    Sanction s =
        new Sanction(
            UUID.randomUUID(),
            c.npi(),
            action,
            c.reason(),
            c.effectiveDate(),
            c.sourceFeed(),
            recordedBy,
            clock.instant());
    switch (provider.change(
        s.npi(), action.statusChange(), s.reason(), s.effectiveDate(), s.sanctionId())) {
      case NOT_FOUND -> throw ApiException.notFound("No provider found for NPI " + s.npi());
      case FAILED ->
          throw new ApiException(
              HttpStatus.SERVICE_UNAVAILABLE,
              null,
              "The sanction for NPI "
                  + s.npi()
                  + " could not be applied: provider-service did not change the status. Nothing was"
                  + " recorded; try again later.",
              List.of());
      case APPLIED -> {
        // recorded below
      }
    }
    tx.executeWithoutResult(
        status -> {
          history.add(s);
          outbox.enqueue(
              "SanctionApplied",
              s.npi(),
              new SanctionAppliedData(
                  s.sanctionId(), s.npi(), action.name(), s.reason(), s.effectiveDate()));
          inTransaction.accept(s);
        });
    return s;
  }

  private static ApiException unprocessable(String detail) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, null, detail, List.of());
  }
}
