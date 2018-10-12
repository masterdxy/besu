package tech.pegasys.pantheon.crypto.altbn128;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.Test;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128Fq12PairerTest {

  @Test
  public void shouldEqualOneWhenNegatedPairsAreMultiplied() {
    final Fq12 p1Paired = AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2());
    final Fq12 p1Finalzied = AltBn128Fq12Pairer.finalize(p1Paired);

    final Fq12 pn1Paired =
        AltBn128Fq12Pairer.pair(AltBn128Point.g1().negate(), AltBn128Fq2Point.g2());
    final Fq12 pn1Finalzied = AltBn128Fq12Pairer.finalize(pn1Paired);

    assertThat(p1Finalzied.multiply(pn1Finalzied)).isEqualTo(Fq12.one());
  }

  @Test
  public void shouldEqualOneWhenNegatedPairsAreMultipliedBothWays() {
    final Fq12 p1Paired = AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2());
    final Fq12 p1Finalized = AltBn128Fq12Pairer.finalize(p1Paired);
    final Fq12 pn1Paired =
        AltBn128Fq12Pairer.pair(AltBn128Point.g1().negate(), AltBn128Fq2Point.g2());
    final Fq12 pn1Finalized = AltBn128Fq12Pairer.finalize(pn1Paired);
    final Fq12 np1Paired =
        AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2().negate());
    final Fq12 np1Finalized = AltBn128Fq12Pairer.finalize(np1Paired);

    assertThat(p1Finalized.multiply(np1Finalized)).isEqualTo(Fq12.one());
    assertThat(pn1Finalized).isEqualTo(np1Finalized);
  }

  @Test
  public void shouldEqualOneWhenRaisedToCurveOrder() {
    final Fq12 p1Paired = AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2());
    final Fq12 p1Finalized = AltBn128Fq12Pairer.finalize(p1Paired);

    final BigInteger curveOrder =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495617");
    assertThat(p1Finalized.power(curveOrder)).isEqualTo(Fq12.one());
  }

  @Test
  public void shouldBeBilinear() {
    final Fq12 p1Paired = AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2());
    final Fq12 p1Finalized = AltBn128Fq12Pairer.finalize(p1Paired);
    final Fq12 p2Paired =
        AltBn128Fq12Pairer.pair(
            AltBn128Point.g1().multiply(BigInteger.valueOf(2)), AltBn128Fq2Point.g2());
    final Fq12 p2Finalized = AltBn128Fq12Pairer.finalize(p2Paired);

    assertThat(p1Finalized.multiply(p1Finalized)).isEqualTo(p2Finalized);
  }

  @Test
  public void shouldBeNongenerate() {
    final Fq12 p1Paired = AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2());
    final Fq12 p1Finalized = AltBn128Fq12Pairer.finalize(p1Paired);
    final Fq12 p2Paired =
        AltBn128Fq12Pairer.pair(
            AltBn128Point.g1().multiply(BigInteger.valueOf(2)), AltBn128Fq2Point.g2());
    final Fq12 p2Finalized = AltBn128Fq12Pairer.finalize(p2Paired);
    final Fq12 np1Paired =
        AltBn128Fq12Pairer.pair(AltBn128Point.g1(), AltBn128Fq2Point.g2().negate());
    final Fq12 np1Finalized = AltBn128Fq12Pairer.finalize(np1Paired);

    assertThat(p1Finalized).isNotEqualTo(p2Finalized);
    assertThat(p1Finalized).isNotEqualTo(np1Finalized);
    assertThat(p2Finalized).isNotEqualTo(np1Finalized);
  }
}
