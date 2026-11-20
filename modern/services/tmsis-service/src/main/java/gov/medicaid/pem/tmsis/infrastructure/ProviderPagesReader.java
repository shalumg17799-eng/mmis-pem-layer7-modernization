package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import java.util.Collections;
import java.util.Iterator;
import org.springframework.batch.item.ItemReader;

/**
 * Reads every ACTIVE provider through provider-service, page after page of at most 200, until the
 * last page (MAM-71 AC-5: no provider missed or repeated). Replaces the sequential READ NEXT of the
 * master (PRVTMS01 READ-MASTER). One reader per step execution (step scope).
 */
public class ProviderPagesReader implements ItemReader<ProviderRow> {

  private final ActiveProviders providers;
  private Iterator<ProviderRow> current = Collections.emptyIterator();
  private int nextPage;
  private boolean lastPageRead;

  public ProviderPagesReader(ActiveProviders providers) {
    this.providers = providers;
  }

  @Override
  public ProviderRow read() {
    while (!current.hasNext()) {
      if (lastPageRead) {
        return null;
      }
      ActiveProviders.Page page = providers.page(nextPage, ActiveProviders.PAGE_SIZE);
      nextPage++;
      current = page.items().iterator();
      lastPageRead =
          page.items().size() < ActiveProviders.PAGE_SIZE
              || (long) nextPage * ActiveProviders.PAGE_SIZE >= page.total();
    }
    return current.next();
  }
}
