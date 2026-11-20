package gov.medicaid.pem.screening.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * PRVSCR01 106-113: the screening passes only when every one of the four sources is P
 * (R-PRVSCR01-cbl-00010).
 */
public final class Verdict {

  private Verdict() {}

  public static String of(List<SourceResult> results) {
    Set<Source> passed = EnumSet.noneOf(Source.class);
    for (SourceResult r : results) {
      if (!"P".equals(r.result())) {
        return "FAIL";
      }
      passed.add(r.source());
    }
    return passed.size() == Source.values().length ? "PASS" : "FAIL";
  }
}
