package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.item.ItemReader;

/**
 * Reads every ACTIVE provider through provider-service by key, at most 200 at a time, until the
 * last answer (MAM-71 AC-5: no provider missed or repeated; contract provider-service 1.2.0,
 * Q-132). The first request has no key, each next one has the NPI of the last row of the answer
 * before, so a provider that is approved or terminated while the run reads cannot shift what comes
 * next (paging by page number could miss or repeat one). Replaces the sequential READ NEXT of the
 * master (PRVTMS01 READ-MASTER). One reader per step execution (step scope).
 *
 * <p>The walk ends only at the answer itself: an answer with no provider, or with fewer than 200,
 * is the last one (contract provider-service 1.2.0). The total never ends it: a provider that
 * leaves ACTIVE after it was read and one that enters after the key keep the total while the list
 * shifts, so the rows read can reach or pass the total while a provider that stayed ACTIVE is still
 * to come. An exact multiple of 200 providers costs one more request, the one that gets the empty
 * answer, and its total is checked like that of every other answer.
 *
 * <p>Safety net: the run must not write a file from data it cannot trust, so the read fails, with a
 * short reason (the run is then FAILED, D-024), when
 *
 * <ul>
 *   <li>the total of an answer differs from the total of the first one: a provider entered or left
 *       ACTIVE during the run, and a file would mix two moments (the safer default of Q-132: run
 *       the extract again); or
 *   <li>the NPIs are not strictly ascending, inside an answer or after the key: a repeated NPI, an
 *       answer that does not respect the key (a provider-service older than 1.2.0 ignores it).
 * </ul>
 *
 * One provider that leaves and one that enters ACTIVE keep the total and cannot be seen; by key
 * every provider that stays ACTIVE is still read once.
 */
public class ProviderPagesReader implements ItemReader<ProviderRow> {

  /** The reason of a run whose provider data changed while it read (D-024, Q-132). */
  static final String DATA_CHANGED = "provider data changed during the extract; run it again";

  static final String NOT_ASCENDING =
      "provider-service did not answer in ascending NPI order after the key";

  private final ActiveProviders providers;
  private Iterator<ProviderRow> current = Collections.emptyIterator();
  private String lastNpi;
  private Long totalAtStart;
  private boolean lastAnswerRead;

  public ProviderPagesReader(ActiveProviders providers) {
    this.providers = providers;
  }

  @Override
  public ProviderRow read() {
    while (!current.hasNext()) {
      if (lastAnswerRead) {
        return null;
      }
      readNextAnswer();
    }
    return current.next();
  }

  private void readNextAnswer() {
    ActiveProviders.Page answer = providers.after(lastNpi, ActiveProviders.PAGE_SIZE);
    List<ProviderRow> items = answer.items();
    checkTotal(answer.total());
    checkAscending(items);
    current = items.iterator();
    if (!items.isEmpty()) {
      lastNpi = items.get(items.size() - 1).npi();
    }
    lastAnswerRead = items.size() < ActiveProviders.PAGE_SIZE;
  }

  /** The total of every answer is the total of the first one. */
  private void checkTotal(long total) {
    if (totalAtStart == null) {
      totalAtStart = total;
    } else if (totalAtStart != total) {
      throw new IllegalStateException(DATA_CHANGED);
    }
  }

  /** The NPIs of the answer ascend strictly, and the first one is greater than the key. */
  private void checkAscending(List<ProviderRow> items) {
    String previous = lastNpi;
    for (ProviderRow row : items) {
      if (previous != null && row.npi().compareTo(previous) <= 0) {
        throw new IllegalStateException(NOT_ASCENDING);
      }
      previous = row.npi();
    }
  }
}
