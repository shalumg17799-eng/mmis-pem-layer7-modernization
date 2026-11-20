package gov.medicaid.pem.fixedwidth;

/**
 * The legacy field types (synthetic-data skill section 2): PIC X right-padded with spaces, PIC 9
 * display left zero-filled, an 8-digit CCYYMMDD date (D-004) and the 5-digit YYDDD date that only
 * the legacy T-MSIS row uses (D-015).
 */
public enum FieldType {
  ALPHANUMERIC,
  NUMERIC,
  DATE_CCYYMMDD,
  DATE_YYDDD;

  boolean digits() {
    return this != ALPHANUMERIC;
  }
}
