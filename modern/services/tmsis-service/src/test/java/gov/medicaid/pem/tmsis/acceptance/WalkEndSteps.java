package gov.medicaid.pem.tmsis.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import io.cucumber.java.en.Given;
import java.util.ArrayList;
import java.util.List;

/**
 * One more step for MAM-71 AC-5 (code review of tmsis-service 2026-11-21, MUST-FIX 1): providers
 * whose NPIs are not next to each other, so that a provider can enter ACTIVE between two that are
 * already there while the run reads (a new class beside {@link ExtractSteps}, Q-097 C: nothing
 * recorded is edited). The hooks and the other steps are those of {@link ExtractSteps}.
 */
public class WalkEndSteps {

  @Given(
      "provider-service holds {int} ACTIVE providers with every second NPI from {string} to {string}")
  public void holdsEverySecondNpi(int count, String from, String to) {
    List<Prov> all = new ArrayList<>();
    long first = Long.parseLong(from);
    for (int i = 0; i < count; i++) {
      all.add(Prov.active(Long.toString(first + 2L * i)));
    }
    assertThat(all.get(all.size() - 1).npi()).isEqualTo(to);
    TestProviderService.holds(all);
  }
}
