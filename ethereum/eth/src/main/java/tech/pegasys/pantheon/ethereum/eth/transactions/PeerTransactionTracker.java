package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.util.Collections.emptySet;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer.DisconnectCallback;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class PeerTransactionTracker implements DisconnectCallback {
  private static final int MAX_TRACKED_SEEN_TRANSACTIONS = 30_000;
  private final Map<EthPeer, Set<Hash>> seenTransactions = new ConcurrentHashMap<>();
  private final Map<EthPeer, Set<Transaction>> transactionsToSend = new ConcurrentHashMap<>();

  public synchronized void markTransactionsAsSeen(
      final EthPeer peer, final Collection<Transaction> transactions) {
    final Set<Hash> seenTransactionsForPeer = getOrCreateSeenTransactionsForPeer(peer);
    transactions.stream().map(Transaction::hash).forEach(seenTransactionsForPeer::add);
  }

  public synchronized void addToPeerSendQueue(final EthPeer peer, final Transaction transaction) {
    if (!hasPeerSeenTransaction(peer, transaction)) {
      transactionsToSend.computeIfAbsent(peer, key -> createTransactionsSet()).add(transaction);
    }
  }

  public Iterable<EthPeer> getEthPeersWithUnsentTransactions() {
    return transactionsToSend.keySet();
  }

  public synchronized Set<Transaction> claimTransactionsToSendToPeer(final EthPeer peer) {
    final Set<Transaction> transactionsToSend = this.transactionsToSend.remove(peer);
    if (transactionsToSend != null) {
      markTransactionsAsSeen(peer, transactionsToSend);
      return transactionsToSend;
    } else {
      return emptySet();
    }
  }

  private Set<Hash> getOrCreateSeenTransactionsForPeer(final EthPeer peer) {
    return seenTransactions.computeIfAbsent(peer, key -> createTransactionsSet());
  }

  private boolean hasPeerSeenTransaction(final EthPeer peer, final Transaction transaction) {
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    return seenTransactionsForPeer != null && seenTransactionsForPeer.contains(transaction.hash());
  }

  private <T> Set<T> createTransactionsSet() {
    return Collections.newSetFromMap(
        new LinkedHashMap<T, Boolean>(1 << 4, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(final Map.Entry<T, Boolean> eldest) {
            return size() > MAX_TRACKED_SEEN_TRANSACTIONS;
          }
        });
  }

  @Override
  public void onDisconnect(final EthPeer peer) {
    seenTransactions.remove(peer);
    transactionsToSend.remove(peer);
  }
}
