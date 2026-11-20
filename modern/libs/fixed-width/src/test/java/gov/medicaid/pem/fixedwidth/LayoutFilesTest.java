package gov.medicaid.pem.fixedwidth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.fixedwidth.DataLayoutsDoc.Row;
import gov.medicaid.pem.fixedwidth.DataLayoutsDoc.Section;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-42 AC-1: the layouts are exported from the graph, not typed. The files are read straight from
 * src/main/resources/layouts, so these tests pin the JSON keys the exporter writes: {@code name},
 * {@code length}, {@code readQuery} (the Cypher read query that produced the file) and {@code
 * fields[]} with {@code name}, {@code start} (1-based) and {@code length}.
 */
class LayoutFilesTest {

  /** The working folder of the Gradle test task is the module folder. */
  static final Path LAYOUT_DIR = Path.of("src", "main", "resources", "layouts");

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern WRITE_CLAUSE =
      Pattern.compile(
          "\\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP)\\b", Pattern.CASE_INSENSITIVE);

  /**
   * A field as placed in a record; every filler is called FILLER (the graph names them
   * FILLER@L<line>).
   */
  private record Placed(String name, int start, int length) {}

  static Stream<Arguments> layouts() {
    return Stream.of(
        arguments("PRVRECRD", 130),
        arguments("SCRNREC", 67),
        arguments("APPINPUT", 200),
        arguments("APPERROR", 250),
        arguments("SANCIN", 80),
        arguments("REVALQ", 80),
        arguments("TMSISOUT", 120),
        arguments("DIROUT", 100));
  }

  @ParameterizedTest(name = "{0}.json exists")
  @MethodSource("layouts")
  @DisplayName("[MAM-42 AC-1] there is one JSON layout file for each legacy record")
  void layoutFileExists(String name, int length) {
    assertThat(LAYOUT_DIR.resolve(name + ".json")).isRegularFile();
  }

  @ParameterizedTest(name = "{0} is {1} bytes")
  @MethodSource("layouts")
  @DisplayName("[MAM-42 AC-1] each layout names its record and its length")
  void layoutNamesRecordAndLength(String name, int length) throws IOException {
    JsonNode layout = read(name);
    assertThat(layout.path("name").asText()).isEqualTo(name);
    assertThat(layout.path("length").asInt(-1)).isEqualTo(length);
  }

  @ParameterizedTest(name = "{0} names its read query")
  @MethodSource("layouts")
  @DisplayName("[MAM-42 AC-1] each layout names the graph read query that produced it")
  void layoutNamesItsReadQuery(String name, int length) throws IOException {
    String query = read(name).path("readQuery").asText("");
    assertThat(query).containsIgnoringCase("MATCH").containsIgnoringCase("RETURN");
  }

  @ParameterizedTest(name = "{0} read query only reads")
  @MethodSource("layouts")
  @DisplayName("[MAM-42 AC-1] the read query is a read: no CREATE, MERGE, SET, DELETE or REMOVE")
  void readQueryOnlyReads(String name, int length) throws IOException {
    String query = read(name).path("readQuery").asText("");
    assertThat(WRITE_CLAUSE.matcher(query).find()).isFalse();
  }

  @ParameterizedTest(name = "{0} is documented as {1}")
  @CsvSource({
    "PRVRECRD,PRVRECRD:PROVIDER-RECORD",
    "SCRNREC,SCRNREC:SCREENING-RECORD",
    "SANCIN,PRVSAN01:SANCTION-IN-RECORD",
    "REVALQ,PRVREV01:REVAL-OUT-RECORD",
    "TMSISOUT,PRVTMS01:WS-TMSIS-DETAIL",
    "DIROUT,PRVDIR01:DIR-OUT-RECORD"
  })
  @DisplayName(
      "[MAM-42 AC-1] every field in data-layouts.md is exported at its documented position")
  void everyDocumentedFieldIsExported(String name, String docSection) throws IOException {
    List<Placed> documented =
        documentedRows(docSection).stream()
            .filter(row -> !row.group())
            .map(LayoutFilesTest::placed)
            .toList();
    assertThat(exportedFields(name)).containsAll(documented);
  }

  @ParameterizedTest(name = "{0} adds nothing to {1}")
  @CsvSource({
    "PRVRECRD,PRVRECRD:PROVIDER-RECORD",
    "SCRNREC,SCRNREC:SCREENING-RECORD",
    "SANCIN,PRVSAN01:SANCTION-IN-RECORD",
    "REVALQ,PRVREV01:REVAL-OUT-RECORD",
    "TMSISOUT,PRVTMS01:WS-TMSIS-DETAIL",
    "DIROUT,PRVDIR01:DIR-OUT-RECORD"
  })
  @DisplayName("[MAM-42 AC-1] no exported field sits where data-layouts.md does not put it")
  void noExportedFieldIsOffThePage(String name, String docSection) throws IOException {
    List<Placed> everythingDocumented =
        documentedRows(docSection).stream().map(LayoutFilesTest::placed).toList();
    assertThat(everythingDocumented).containsAll(exportedFields(name));
  }

  @ParameterizedTest(name = "{0} is declared as {1}")
  @CsvSource({"APPINPUT,PRVENR01:APP-INPUT-RECORD", "APPERROR,PRVENR01:APP-ERROR-RECORD"})
  @DisplayName(
      "[MAM-42 AC-1] the intake and error records have the length data-layouts.md declares")
  void flatRecordLengthsMatchTheDocument(String name, String docSection) throws IOException {
    Section section = DataLayoutsDoc.read().get(docSection);
    assertThat(section).as("section " + docSection + " in data-layouts.md").isNotNull();
    assertThat(read(name).path("length").asInt(-1)).isEqualTo(section.declaredBytes());
  }

  @ParameterizedTest(name = "{0}: {1} bytes, every byte in one field")
  @MethodSource("layouts")
  @DisplayName(
      "[MAM-42 AC-1] the always-present fields of each layout cover every byte exactly once")
  void fieldsCoverTheRecord(String name, int length) {
    List<FieldDef> always =
        FixedWidthCodec.forLayout(name).layout().fields().stream()
            .filter(field -> field.views().isEmpty())
            .sorted(Comparator.comparingInt(FieldDef::start))
            .toList();
    int nextByte = 1;
    for (FieldDef field : always) {
      assertThat(field.start()).as("start of " + field.name()).isEqualTo(nextByte);
      nextByte = field.start() + field.length();
    }
    assertThat(nextByte - 1).as("last byte covered").isEqualTo(length);
  }

  private static JsonNode read(String name) throws IOException {
    Path file = LAYOUT_DIR.resolve(name + ".json");
    assertThat(file).as("layout file for " + name).isRegularFile();
    return JSON.readTree(file.toFile());
  }

  private static List<Placed> exportedFields(String name) throws IOException {
    JsonNode fields = read(name).path("fields");
    assertThat(fields.isArray()).as(name + " has a fields array").isTrue();
    List<Placed> placed = new ArrayList<>();
    for (JsonNode field : fields) {
      placed.add(
          new Placed(
              normalise(field.path("name").asText()),
              field.path("start").asInt(-1),
              field.path("length").asInt(-1)));
    }
    return placed;
  }

  private static List<Row> documentedRows(String docSection) {
    Section section = DataLayoutsDoc.read().get(docSection);
    assertThat(section).as("section " + docSection + " in data-layouts.md").isNotNull();
    assertThat(section.rows()).as("field rows of " + docSection).isNotEmpty();
    return section.rows();
  }

  private static Placed placed(Row row) {
    return new Placed(normalise(row.name()), row.start(), row.length());
  }

  private static String normalise(String name) {
    return name.startsWith("FILLER") ? "FILLER" : name;
  }
}
