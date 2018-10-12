package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueProposerSelector;
import tech.pegasys.pantheon.consensus.common.ValidatorProvider;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;

public class CliqueHelpers {

  public static Address getProposerOfBlock(final BlockHeader header) {
    final CliqueExtraData extraData = CliqueExtraData.decode(header.getExtraData());
    return CliqueBlockHashing.recoverProposerAddress(header, extraData);
  }

  public static List<Address> getValidatorsOfBlock(final BlockHeader header) {
    final BytesValue extraData = header.getExtraData();
    final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(extraData);
    return cliqueExtraData.getValidators();
  }

  public static Address getProposerForBlockAfter(
      final BlockHeader parent, final VoteTallyCache voteTallyCache) {
    final CliqueProposerSelector proposerSelector = new CliqueProposerSelector(voteTallyCache);
    return proposerSelector.selectProposerForNextBlock(parent);
  }

  public static boolean addressIsAllowedToProduceNextBlock(
      final Address candidate,
      final ProtocolContext<CliqueContext> protocolContext,
      final BlockHeader parent) {
    final VoteTally validatorProvider =
        protocolContext.getConsensusState().getVoteTallyCache().getVoteTallyAtBlock(parent);

    if (!validatorProvider.getCurrentValidators().contains(candidate)) {
      return false;
    }

    final int minimumUnsignedPastBlocks = minimumBlocksSincePreviousSigning(validatorProvider);

    final Blockchain blockchain = protocolContext.getBlockchain();
    int unsignedBlockCount = 0;
    BlockHeader localParent = parent;

    while (unsignedBlockCount < minimumUnsignedPastBlocks) {

      if (localParent.getNumber() == 0) {
        return true;
      }

      final Address parentSigner = CliqueHelpers.getProposerOfBlock(localParent);
      if (parentSigner.equals(candidate)) {
        return false;
      }
      unsignedBlockCount++;

      localParent =
          blockchain
              .getBlockHeader(localParent.getParentHash())
              .orElseThrow(() -> new IllegalStateException("The block was on a orphaned chain."));
    }

    return true;
  }

  private static int minimumBlocksSincePreviousSigning(final ValidatorProvider validatorProvider) {
    final int validatorCount = validatorProvider.getCurrentValidators().size();
    // The number of contiguous blocks in which a signer may only sign 1 (as taken from clique spec)
    final int signerLimit = (validatorCount / 2) + 1;
    return signerLimit - 1;
  }
}
