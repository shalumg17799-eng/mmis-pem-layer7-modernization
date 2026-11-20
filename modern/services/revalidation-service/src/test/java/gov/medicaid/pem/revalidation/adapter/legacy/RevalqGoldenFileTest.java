package gov.medicaid.pem.revalidation.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.SweepDecision;
import gov.medicaid.pem.revalidation.domain.SweepRule;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The legacy REVALQ checked against the real output of the legacy job (parity golden run
 * PRVMTH01 20261120-seed, modern/parity/golden/PRVMTH01/20261120-seed): the 130-byte provider master
 * that STEP010 PRVREV01 read (16 synthetic providers in every status), the 80-byte REVALQ it wrote
 * (4 records, translated from EBCDIC to ASCII), and the business date of that run, 2026-11-20 (the
 * MVS system date; no control card exists, see the run's manifest.json). The sweep rule and the
 * file writer of revalidation-service, given that master and that date, must produce those bytes
 * (cobol-to-java section 7: a golden-file test once golden files exist; Phase 9 compares the same
 * files).
 *
 * <p>Where the legacy and the modern rule differ (an ACTIVE provider with no due date, D-029; a
 * provider that already has an open case) the seed master has no such provider, so the comparison
 * is exact.
 */
class RevalqGoldenFileTest {

  static final LocalDate LEGACY_RUN_DATE = LocalDate.of(2026, 11, 20);
  static final String RUN = "parity/golden/PRVMTH01/20261120-seed/";
  static final int MASTER_RECORD = 130;

  /** The golden file, found from the module's folder upwards (modern/ or the repository root). */
  static Path golden(String relative) {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path base : List.of(dir, dir.resolve("modern"))) {
        Path candidate = base.resolve(RUN + relative);
        if (Files.isRegularFile(candidate)) {
          return candidate;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("golden file not found: " + RUN + relative);
  }

  static byte[] bytes(String relative) {
    try {
      return Files.readAllBytes(golden(relative));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The master as the legacy job read it: 130-byte records, each followed by a line feed. */
  static List<TrackedProvider> legacyMaster() {
    FixedWidthCodec master = FixedWidthCodec.forLayout("PRVRECRD");
    byte[] all = bytes("inputs/provider-master-seed-sorted.dat");
    List<TrackedProvider> providers = new ArrayList<>();
    int at = 0;
    for (int n = 1; at + MASTER_RECORD <= all.length; n++) {
      FixedRecord r = master.read(Arrays.copyOfRange(all, at, at + MASTER_RECORD), n);
      providers.add(
          new TrackedProvider(
              r.get("PRV-NPI"), r.get("PRV-ENROLL-STATUS"), r.getDate("PRV-REVAL-DUE-DATE")));
      at += MASTER_RECORD;
      while (at < all.length && (all[at] == '
' || all[at] == '
')) {
        at++; // the line end of the text copy: LF, or CRLF when the file was checked out so
      }
    }
    return providers;
  }

  static List<RevalidationCase> queuedBySweep() {
    return legacyMaster().stream()
        .filter(p -> SweepRule.decide(p, LEGACY_RUN_DATE) == SweepDecision.QUEUE)
        .map(
            p ->
                new RevalidationCase(
                    UUID.randomUUID(),
                    p.npi(),
                    p.dueDate(),
                    CaseStatus.DUE,
                    Instant.parse("2026-11-20T10:00:00Z"),
                    null))
        .toList();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] the REVALQ written for the master of the legacy golden run is the legacy REVALQ, byte for byte")
  void sameBytesAsTheLegacyRun() {
    byte[] legacy = bytes("after/revalq.ebcdic.ascii");

    byte[] modern = new RevalqFile().write(queuedBySweep());

    assertThat(legacy).as("the golden file: 4 records of 80 bytes").hasSize(320);
    assertThat(modern).isEqualTo(legacy);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-2] the providers the legacy job queued are exactly the ones the sweep rule queues for 2026-11-20")
  void sameProvidersAsTheLegacyRun() {
    FixedWidthCodec revalq = FixedWidthCodec.forLayout("REVALQ");
    byte[] legacy = bytes("after/revalq.ebcdic.ascii");
    List<String> legacyNpis = new ArrayList<>();
    for (int at = 0, n = 1; at < legacy.length; at += 80, n++) {
      legacyNpis.add(revalq.read(Arrays.copyOfRange(legacy, at, at + 80), n).get("REV-NPI"));
    }

    assertThat(queuedBySweep())
        .extracting(RevalidationCase::npi)
        .containsExactlyElementsOf(legacyNpis)
        .containsExactly("1005923986", "1005947811", "1005951276", "1005989276");
    assertThat(queuedBySweep())
        .extracting(RevalidationCase::dueDate)
        .containsExactly(
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 2, 15),
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2025, 12, 1));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-3] on the legacy master only the 4 ACTIVE providers whose date has arrived are queued: 4 ACTIVE not yet due and 8 pending, denied, terminated or suspended are not")
  void decisionsOnTheLegacyMaster() {
    Map<SweepDecision, Integer> counts = new EnumMap<>(SweepDecision.class);
    for (TrackedProvider p : legacyMaster()) {
      counts.merge(SweepRule.decide(p, LEGACY_RUN_DATE), 1, Integer::sum);
    }

    assertThat(legacyMaster()).hasSize(16);
    assertThat(counts)
        .containsEntry(SweepDecision.QUEUE, 4)
        .containsEntry(SweepDecision.NOT_DUE, 4)
        .containsEntry(SweepDecision.NOT_ACTIVE, 8)
        .doesNotContainKey(SweepDecision.NO_DUE_DATE);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-3] a terminated provider that is long overdue (2024-02-01) and a suspended one are left out, as the legacy job left them out")
  void overdueButNotActiveIsLeftOut() {
    Map<String, SweepDecision> byNpi = new java.util.LinkedHashMap<>();
    legacyMaster().forEach(p -> byNpi.put(p.npi(), SweepRule.decide(p, LEGACY_RUN_DATE)));

    assertThat(byNpi)
        .containsEntry("1005918507", SweepDecision.NOT_ACTIVE) // T, due 2024-02-01
        .containsEntry("1005932946", SweepDecision.NOT_ACTIVE) // T, due 2025-03-01
        .containsEntry("1005919109", SweepDecision.NOT_ACTIVE) // S, due 2027-09-01
        .containsEntry("1005925213", SweepDecision.NOT_ACTIVE) // P
        .containsEntry("1005914035", SweepDecision.NOT_ACTIVE) // D, due 9999-12-31
        .containsEntry("1005912765", SweepDecision.NOT_DUE) // A, due 2028-04-01
        .containsEntry("1005989276", SweepDecision.QUEUE); // A, due 2025-12-01
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] the legacy REVALQ read with the REVALQ layout and written back gives the same 320 bytes (round trip)")
  void roundTripOfTheGoldenFile() {
    FixedWidthCodec revalq = FixedWidthCodec.forLayout("REVALQ");
    byte[] legacy = bytes("after/revalq.ebcdic.ascii");
    ByteArrayOutputStream again = new ByteArrayOutputStream();

    for (int at = 0, n = 1; at < legacy.length; at += 80, n++) {
      FixedRecord record = revalq.read(Arrays.copyOfRange(legacy, at, at + 80), n);
      assertThat(record.get("REV-ACTION")).isEqualTo("RESCREEN-REQUIRED");
      again.writeBytes(revalq.write(record));
    }

    assertThat(again.toByteArray()).isEqualTo(legacy);
    assertThat(new String(legacy, 18, 20, StandardCharsets.ISO_8859_1))
        .isEqualTo("RESCREEN-REQUIRED   ");
  }
}
