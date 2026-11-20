package gov.medicaid.pem.screening.domain;

/**
 * The four screening sources of PRVSCR01 (CHECK-LICENSE, -LEIE, -SAM, -SSA-DMF); NPPES is not one
 * (Q-042).
 */
public enum Source {
  LICBOARD,
  OIGLEIE,
  SAMGOV,
  SSADMF
}
