package gov.medicaid.pem.fixedwidth;

/**
 * An explicit codec error (D-024): it names the layout, the record number (0 for a record built in
 * memory) and the field (null for a whole-record error such as a wrong length).
 */
public class FixedWidthException extends RuntimeException {

  private final String layout;
  private final long recordNumber;
  private final String field;

  public FixedWidthException(String layout, long recordNumber, String field, String message) {
    super(message);
    this.layout = layout;
    this.recordNumber = recordNumber;
    this.field = field;
  }

  public String layout() {
    return layout;
  }

  public long recordNumber() {
    return recordNumber;
  }

  public String field() {
    return field;
  }
}
