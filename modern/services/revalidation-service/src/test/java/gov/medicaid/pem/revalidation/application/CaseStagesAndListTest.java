package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two small use cases: the DUE to IN_SCREENING step once RevalidationDue is out (MAM-49 AC-1,
 * question C-13) and the list of cases (MAM-48 AC-1). The store is the port, so it is a mock here:
 * the tests say what the use case asks of it and what it gives back.
 */
class CaseStagesAndListTest {

  // ---- MAM-49 AC-1 -----------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-49 AC-1] a RevalidationDue that has been published moves that case on: true when it moved")
  void movesTheCaseOn() {
    UUID caseId = UUID.randomUUID();
    CaseStages stages = mock(CaseStages.class);
    when(stages.markInScreening(caseId)).thenReturn(true);

    assertThat(new RequestScreeningStage(stages).revalidationDuePublished(caseId)).isTrue();

    verify(stages).markInScreening(caseId);
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-1] false when the case is unknown or no longer DUE (the step is not repeated)")
  void falseWhenNothingMoved() {
    UUID caseId = UUID.randomUUID();
    CaseStages stages = mock(CaseStages.class);
    when(stages.markInScreening(caseId)).thenReturn(false);

    assertThat(new RequestScreeningStage(stages).revalidationDuePublished(caseId)).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-1] with the store's rule the step happens once: DUE to IN_SCREENING, then never again")
  void theStepHappensOnce() {
    InMemoryStore store = new InMemoryStore();
    RevalidationCase c = store.addCase("1003000222", CaseStatus.DUE, LocalDate.of(2026, 2, 28));
    RequestScreeningStage stage = new RequestScreeningStage(store);

    assertThat(stage.revalidationDuePublished(c.caseId())).isTrue();
    assertThat(store.caseOf(c.caseId()).status()).isEqualTo(CaseStatus.IN_SCREENING);
    assertThat(stage.revalidationDuePublished(c.caseId())).isFalse();
    assertThat(store.caseOf(c.caseId()).status()).isEqualTo(CaseStatus.IN_SCREENING);
  }

  // ---- MAM-48 AC-1 -----------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-48 AC-1] the list asks the store for the filters and the page it was given")
  void listPassesTheFiltersAndThePage() {
    CaseStore store = mock(CaseStore.class);
    RevalidationCase c =
        new RevalidationCase(
            UUID.randomUUID(),
            "1003000222",
            LocalDate.of(2026, 2, 28),
            CaseStatus.DUE,
            Instant.parse("2026-03-31T08:00:00Z"),
            null);
    CasePage page = new CasePage(List.of(c), 3, 7, 24);
    when(store.find("1003000222", CaseStatus.DUE, 3, 7)).thenReturn(page);

    CasePage answered = new ListCases(store).page("1003000222", CaseStatus.DUE, 3, 7);

    assertThat(answered).isSameAs(page);
    verify(store).find("1003000222", CaseStatus.DUE, 3, 7);
  }

  @Test
  @DisplayName("[MAM-48 AC-1] no filter is passed on as no filter")
  void noFilters() {
    CaseStore store = mock(CaseStore.class);
    CasePage empty = new CasePage(List.of(), 0, 20, 0);
    when(store.find(null, null, 0, 20)).thenReturn(empty);

    assertThat(new ListCases(store).page(null, null, 0, 20)).isSameAs(empty);
  }

  @Test
  @DisplayName("[MAM-48 AC-1] a page holds its items, page number, page size and the total")
  void pageRecord() {
    CasePage page = new CasePage(List.of(), 2, 50, 123);

    assertThat(page.items()).isEmpty();
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.total()).isEqualTo(123L);
  }

  @Test
  @DisplayName("[MAM-45 AC-4] the RevalidationDue data holds the case id, the NPI and the due date")
  void revalidationDueData() {
    UUID caseId = UUID.randomUUID();
    RevalidationDueData data =
        new RevalidationDueData(caseId, "1003000222", LocalDate.of(2026, 2, 28));

    assertThat(data.caseId()).isEqualTo(caseId);
    assertThat(data.npi()).isEqualTo("1003000222");
    assertThat(data.dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
  }
}
