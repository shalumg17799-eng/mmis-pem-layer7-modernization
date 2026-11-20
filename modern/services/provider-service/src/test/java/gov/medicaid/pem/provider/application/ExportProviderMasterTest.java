package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-43 (D-007, ADR-005): the export asks the store for every provider and writes them as given.
 */
class ExportProviderMasterTest {

  static Provider provider(String npi) {
    return Provider.builder()
        .npi(npi)
        .medicaidId("MCD" + npi.substring(4))
        .providerType(ProviderType.INDIVIDUAL)
        .taxonomy("207Q00000X")
        .enrollStatus(EnrollStatus.ACTIVE)
        .screenFlag(ScreenFlag.PASS)
        .effectiveDate(LocalDate.of(2026, 11, 18))
        .build();
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-2] the providers go to the file writer in the order the store gives them (ascending NPI), and its text is the result")
  void writesEveryProviderInStoreOrder() {
    List<Provider> inKeyOrder =
        List.of(provider("1003000111"), provider("1003000444"), provider("1003000777"));
    List<List<Provider>> written = new ArrayList<>();
    ExportProviderMaster export =
        new ExportProviderMaster(
            () -> inKeyOrder,
            providers -> {
              written.add(providers);
              return "the legacy records";
            });

    String text = export.export();

    assertThat(text).isEqualTo("the legacy records");
    assertThat(written).containsExactly(inKeyOrder);
  }

  @Test
  @DisplayName("[MAM-43 AC-1] with no provider the export is empty: no header and no trailer")
  void emptyMaster() {
    ExportProviderMaster export =
        new ExportProviderMaster(() -> List.of(), providers -> String.valueOf(providers.size()));

    assertThat(export.export()).isEqualTo("0");
  }
}
