package gov.medicaid.pem.revalidation.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-45 AC-7: the REVALQ file (PRVREV01 REVAL-OUT-RECORD, 80 bytes) through the MAM-42 codec. */
class RevalqFileTest {

  static RevalidationCase opened(String npi, LocalDate due) {
    return new RevalidationCase(
        UUID.randomUUID(), npi, due, CaseStatus.DUE, Instant.parse("2026-11-19T10:00:00Z"), null);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] one 80-byte record per opened case in ascending NPI order: NPI, CCYYMMDD due date, RESCREEN-REQUIRED, 42 spaces")
  void records() {
    byte[] file =
        new RevalqFile()
            .write(
                List.of(
                    opened("1003000333", LocalDate.of(2026, 3, 31)),
                    opened("1003000222", LocalDate.of(2026, 2, 28))));
    assertThat(file).hasSize(160);
    String text = new String(file, StandardCharsets.ISO_8859_1);
    assertThat(text.substring(0, 80))
        .isEqualTo("1003000222" + "20260228" + "RESCREEN-REQUIRED   " + " ".repeat(42));
    assertThat(text.substring(80))
        .isEqualTo("1003000333" + "20260331" + "RESCREEN-REQUIRED   " + " ".repeat(42));
    FixedWidthCodec revalq = FixedWidthCodec.forLayout("REVALQ");
    assertThat(revalq.read(text.substring(0, 80).getBytes(StandardCharsets.ISO_8859_1), 1)
            .getDate("REV-DUE-DATE"))
        .isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  @DisplayName("[MAM-45 AC-7] a run that opens no case writes an empty file")
  void empty() {
    assertThat(new RevalqFile().write(List.of())).isEmpty();
  }
}
