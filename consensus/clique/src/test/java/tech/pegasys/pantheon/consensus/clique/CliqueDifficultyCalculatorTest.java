package tech.pegasys.pantheon.consensus.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Util;

import java.math.BigInteger;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueDifficultyCalculatorTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private Address localAddr;

  private final List<Address> validatorList = Lists.newArrayList();
  private ProtocolContext<CliqueContext> cliqueProtocolContext;
  private BlockHeaderTestFixture blockHeaderBuilder;

  @Before
  public void setup() {
    localAddr = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    validatorList.add(localAddr);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddr, 1));

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();

    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(null, null, cliqueContext);
    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void inTurnValidatorProducesDifficultyOfTwo() {
    final CliqueDifficultyCalculator calculator = new CliqueDifficultyCalculator(localAddr);

    final BlockHeader parentHeader = blockHeaderBuilder.number(1).buildHeader();

    assertThat(calculator.nextDifficulty(0, parentHeader, cliqueProtocolContext))
        .isEqualTo(BigInteger.valueOf(2));
  }

  @Test
  public void outTurnValidatorProducesDifficultyOfOne() {
    final CliqueDifficultyCalculator calculator = new CliqueDifficultyCalculator(localAddr);

    final BlockHeader parentHeader = blockHeaderBuilder.number(2).buildHeader();

    assertThat(calculator.nextDifficulty(0, parentHeader, cliqueProtocolContext))
        .isEqualTo(BigInteger.valueOf(1));
  }
}
