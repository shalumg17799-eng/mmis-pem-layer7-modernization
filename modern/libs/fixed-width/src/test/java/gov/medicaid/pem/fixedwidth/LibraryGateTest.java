package gov.medicaid.pem.fixedwidth;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.Samples.Sample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-42 AC-7: {@code ./gradlew :libs:fixed-width:check} exits 0 with the round-trip tests and the
 * quality gates (JaCoCo 85% lines and Spotless come from the shared pem.java-library conventions).
 * The Gradle run itself is the evidence; this test closes the one gap the build cannot see: a
 * layout added to the library without a round-trip test.
 */
class LibraryGateTest {

  @Test
  @DisplayName(
      "[MAM-42 AC-7] every layout the library ships has a round-trip sample, and every sample has a layout")
  void everyLayoutHasARoundTripSample() throws IOException {
    assertThat(LayoutFilesTest.LAYOUT_DIR).isDirectory();
    Set<String> shipped;
    try (Stream<Path> files = Files.list(LayoutFilesTest.LAYOUT_DIR)) {
      shipped =
          files
              .map(file -> file.getFileName().toString())
              .filter(name -> name.endsWith(".json"))
              .map(name -> name.substring(0, name.length() - ".json".length()))
              .collect(Collectors.toCollection(TreeSet::new));
    }
    Set<String> sampled =
        Samples.roundTripSamples().stream()
            .map(Sample::layout)
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(shipped).isEqualTo(sampled);
  }
}
