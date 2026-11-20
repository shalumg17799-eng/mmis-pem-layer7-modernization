package gov.medicaid.pem.fixedwidth;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.Samples.Sample;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-42 AC-5: a sample record for every layout, an IN and a GR record included, is parsed and
 * written again and the bytes are identical. That includes the fillers: the T-MSIS sample starts
 * with the constant PRV01 the legacy program writes into its first filler.
 */
class RoundTripTest {

  static Stream<Sample> samples() {
    return Samples.roundTripSamples().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("samples")
  @DisplayName("[MAM-42 AC-5] parse then write gives identical bytes")
  void parseThenWriteGivesIdenticalBytes(Sample sample) {
    FixedWidthCodec codec = FixedWidthCodec.forLayout(sample.layout());
    byte[] written = codec.write(codec.read(sample.bytes(), 1));
    assertThat(written).isEqualTo(sample.bytes());
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-5] the T-MSIS filler constant PRV01 in bytes 1-5 survives the round trip")
  void tmsisConstantSurvives() {
    FixedWidthCodec codec = FixedWidthCodec.forLayout("TMSISOUT");
    byte[] written = codec.write(codec.read(Samples.bytes(Samples.TMSIS), 1));
    assertThat(Samples.slice(written, 1, 5)).isEqualTo("PRV01");
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-5] an individual and a group record keep their different variants through the round trip")
  void individualAndGroupKeepTheirVariants() {
    FixedWidthCodec codec = FixedWidthCodec.forLayout("PRVRECRD");
    byte[] individual = codec.write(codec.read(Samples.bytes(Samples.IN_MASTER), 1));
    byte[] group = codec.write(codec.read(Samples.bytes(Samples.GR_MASTER), 2));
    assertThat(Samples.slice(individual, 51, 80)).isEqualTo(Samples.IN_VARIANT);
    assertThat(Samples.slice(group, 51, 80)).isEqualTo(Samples.GR_VARIANT);
  }
}
