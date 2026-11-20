package gov.medicaid.pem.provider.api;

import gov.medicaid.pem.provider.ProviderRows;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Provider rows for the keyset paging tests of MAM-37 AC-9 (Q-132): synthetic data only, written
 * straight into provider_db with {@link ProviderRows}.
 */
final class KeysetFixtures {

  private KeysetFixtures() {}

  /** The NPI 1003000 followed by the three digits, for example {@code npi("444")} = 1003000444. */
  static String npi(String threeDigits) {
    return "1003000" + threeDigits;
  }

  /**
   * Eight providers 1003000111 to 1003000888, stored out of NPI order on purpose (the order of the
   * answer must come from the NPI, not from the order of insertion):
   *
   * <pre>
   * 111 RIVERA MARIA          P (pending)    P (pass)
   * 222 LINDQVIST ASTRID      T (terminated) P
   * 333 NGUYEN LINH           A (active)     P
   * 444 OKAFOR JAMES          P              F (fail)
   * 555 RIVERA JOSE           A              P
   * 666 PATEL ANIKA           A              F
   * 777 SAMPLE HOSPITAL 01    A              P   (a facility)
   * 888 RIVERA ANA            A              P
   * </pre>
   *
   * So: status A = 333 555 666 777 888; name RIVERA = 111 555 888; screen flag F = 444 666.
   */
  static void eight(JdbcTemplate jdbc) {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual("1003000555").name("RIVERA", "JOSE").status("A").flag("P").insert(jdbc);
    ProviderRows.individual("1003000111")
        .name("RIVERA", "MARIA")
        .status("P")
        .flag("P")
        .insert(jdbc);
    ProviderRows.individual("1003000888").name("RIVERA", "ANA").status("A").flag("P").insert(jdbc);
    ProviderRows.individual("1003000333").name("NGUYEN", "LINH").status("A").flag("P").insert(jdbc);
    ProviderRows.group("1003000777")
        .type("FA")
        .legalName("SAMPLE HOSPITAL 01")
        .dbaName("SAMPLE HOSP")
        .status("A")
        .flag("P")
        .insert(jdbc);
    ProviderRows.individual("1003000222")
        .name("LINDQVIST", "ASTRID")
        .status("T")
        .flag("P")
        .insert(jdbc);
    ProviderRows.individual("1003000666").name("PATEL", "ANIKA").status("A").flag("F").insert(jdbc);
    ProviderRows.individual("1003000444")
        .name("OKAFOR", "JAMES")
        .status("P")
        .flag("F")
        .insert(jdbc);
  }

  /** The twenty ACTIVE providers of {@link #concurrent}: 1003002010, 1003002020 ... 1003002200. */
  static final List<String> CONCURRENT_ACTIVE =
      IntStream.rangeClosed(1, 20).mapToObj(i -> String.valueOf(1003002000L + 10L * i)).toList();

  /**
   * The data of the concurrent-change cases: the twenty ACTIVE providers {@link
   * #CONCURRENT_ACTIVE}; two PENDING providers with a passed screening that an analyst can approve
   * (1003002025 and 1003002125); and four that must never appear in a walk over status A:
   * 1003002035 (T), 1003002045 (D), 1003002055 (S) and 1003002065 (PENDING, still unscreened, so it
   * cannot be approved).
   */
  static void concurrent(JdbcTemplate jdbc) {
    ProviderRows.wipe(jdbc);
    for (String npi : CONCURRENT_ACTIVE) {
      ProviderRows.individual(npi)
          .name("ACTIVE" + npi.substring(6), "TESTFIRST")
          .status("A")
          .flag("P")
          .riskTier("M")
          .insert(jdbc);
    }
    for (String npi : new String[] {"1003002025", "1003002125"}) {
      ProviderRows.individual(npi)
          .name("PENDING" + npi.substring(6), "TESTFIRST")
          .status("P")
          .flag("P")
          .riskTier("L")
          .insert(jdbc);
    }
    ProviderRows.individual("1003002035").name("BYSTANDER35", "TESTFIRST").status("T").insert(jdbc);
    ProviderRows.individual("1003002045").name("BYSTANDER45", "TESTFIRST").status("D").insert(jdbc);
    ProviderRows.individual("1003002055").name("BYSTANDER55", "TESTFIRST").status("S").insert(jdbc);
    ProviderRows.individual("1003002065")
        .name("BYSTANDER65", "TESTFIRST")
        .status("P")
        .flag("U")
        .insert(jdbc);
  }

  /**
   * The data of AC-9: 450 ACTIVE providers 1003001001 to 1003001450 and provider 1003001500, which
   * is PENDING with a passed screening (so an analyst can approve it).
   */
  static void ac9(JdbcTemplate jdbc) {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual("1003001500")
        .name("PENDING1500", "TESTFIRST")
        .status("P")
        .flag("P")
        .riskTier("L")
        .insert(jdbc);
    for (long npi = 1003001001L; npi <= 1003001450L; npi++) {
      ProviderRows.individual(String.valueOf(npi))
          .name("ACTIVE" + (npi - 1003001000L), "TESTFIRST")
          .status("A")
          .flag("P")
          .riskTier("L")
          .insert(jdbc);
    }
  }
}
