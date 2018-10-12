package tech.pegasys.pantheon.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.eth.messages.GetBlockBodiesMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.GetBlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.GetReceiptsMessage;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthPeer {
  private static final Logger LOG = LogManager.getLogger();
  private final PeerConnection connection;

  private final int maxTrackedSeenBlocks = 30_000;

  private final Set<Hash> knownBlocks;
  private final String protocolName;
  private final ChainState chainHeadState;
  private final AtomicBoolean statusHasBeenSentToPeer = new AtomicBoolean(false);
  private final AtomicBoolean statusHasBeenReceivedFromPeer = new AtomicBoolean(false);

  private final RequestManager headersRequestManager = new RequestManager(this);
  private final RequestManager bodiesRequestManager = new RequestManager(this);
  private final RequestManager receiptsRequestManager = new RequestManager(this);

  private final AtomicReference<Consumer<EthPeer>> onStatusesExchanged = new AtomicReference<>();
  private final PeerReputation reputation = new PeerReputation();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = new Subscribers<>();

  EthPeer(
      final PeerConnection connection,
      final String protocolName,
      final Consumer<EthPeer> onStatusesExchanged) {
    this.connection = connection;
    this.protocolName = protocolName;
    knownBlocks =
        Collections.newSetFromMap(
            Collections.synchronizedMap(
                new LinkedHashMap<Hash, Boolean>(16, 0.75f, true) {
                  @Override
                  protected boolean removeEldestEntry(final Map.Entry<Hash, Boolean> eldest) {
                    return size() > maxTrackedSeenBlocks;
                  }
                }));
    this.chainHeadState = new ChainState();
    this.onStatusesExchanged.set(onStatusesExchanged);
  }

  public void recordRequestTimeout(final int requestCode) {
    LOG.debug("Timed out while waiting for response from peer {}", this);
    reputation.recordRequestTimeout(requestCode).ifPresent(this::disconnect);
  }

  public void recordUselessResponse() {
    LOG.debug("Received useless response from peer {}", this);
    reputation.recordUselessResponse(System.currentTimeMillis()).ifPresent(this::disconnect);
  }

  public void disconnect(final DisconnectReason reason) {
    connection.disconnect(reason);
  }

  public long subscribeDisconnect(final DisconnectCallback callback) {
    return disconnectCallbacks.subscribe(callback);
  }

  public void unsubscribeDisconnect(final long id) {
    disconnectCallbacks.unsubscribe(id);
  }

  public ResponseStream send(final MessageData messageData) throws PeerNotConnected {
    switch (messageData.getCode()) {
      case EthPV62.GET_BLOCK_HEADERS:
        return sendHeadersRequest(messageData);
      case EthPV62.GET_BLOCK_BODIES:
        return sendBodiesRequest(messageData);
      case EthPV63.GET_RECEIPTS:
        return sendReceiptsRequest(messageData);
      default:
        connection.sendForProtocol(protocolName, messageData);
        return null;
    }
  }

  public ResponseStream getHeadersByHash(
      final Hash hash, final int maxHeaders, final boolean reverse, final int skip)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(hash, maxHeaders, reverse, skip);
    return sendHeadersRequest(message);
  }

  public ResponseStream getHeadersByNumber(
      final long blockNumber, final int maxHeaders, final boolean reverse, final int skip)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(blockNumber, maxHeaders, reverse, skip);
    return sendHeadersRequest(message);
  }

  private ResponseStream sendHeadersRequest(final MessageData messageData) throws PeerNotConnected {
    return headersRequestManager.dispatchRequest(
        () -> connection.sendForProtocol(protocolName, messageData));
  }

  public ResponseStream getBodies(final List<Hash> blockHashes) throws PeerNotConnected {
    final GetBlockBodiesMessage message = GetBlockBodiesMessage.create(blockHashes);
    return sendBodiesRequest(message);
  }

  private ResponseStream sendBodiesRequest(final MessageData messageData) throws PeerNotConnected {
    return bodiesRequestManager.dispatchRequest(
        () -> connection.sendForProtocol(protocolName, messageData));
  }

  public ResponseStream getReceipts(final List<Hash> blockHashes) throws PeerNotConnected {
    final GetReceiptsMessage message = GetReceiptsMessage.create(blockHashes);
    return sendReceiptsRequest(message);
  }

  private ResponseStream sendReceiptsRequest(final MessageData messageData)
      throws PeerNotConnected {
    return receiptsRequestManager.dispatchRequest(
        () -> connection.sendForProtocol(protocolName, messageData));
  }

  boolean validateReceivedMessage(final EthMessage message) {
    checkArgument(message.getPeer().equals(this), "Mismatched message sent to peer for dispatch");
    switch (message.getData().getCode()) {
      case EthPV62.BLOCK_HEADERS:
        if (headersRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited headers received.");
          return false;
        }
        break;
      case EthPV62.BLOCK_BODIES:
        if (bodiesRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited bodies received.");
          return false;
        }
        break;
      case EthPV63.RECEIPTS:
        if (receiptsRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited receipts received.");
          return false;
        }
        break;
      default:
        // Nothing to do
    }
    return true;
  }

  /**
   * Routes messages originating from this peer to listeners.
   *
   * @param message the message to dispatch
   */
  void dispatch(final EthMessage message) {
    checkArgument(message.getPeer().equals(this), "Mismatched message sent to peer for dispatch");
    switch (message.getData().getCode()) {
      case EthPV62.BLOCK_HEADERS:
        reputation.resetTimeoutCount(EthPV62.GET_BLOCK_HEADERS);
        headersRequestManager.dispatchResponse(message);
        break;
      case EthPV62.BLOCK_BODIES:
        reputation.resetTimeoutCount(EthPV62.GET_BLOCK_BODIES);
        bodiesRequestManager.dispatchResponse(message);
        break;
      case EthPV63.RECEIPTS:
        reputation.resetTimeoutCount(EthPV63.GET_RECEIPTS);
        receiptsRequestManager.dispatchResponse(message);
        break;
      default:
        // Nothing to do
    }
  }

  public Map<Integer, AtomicInteger> timeoutCounts() {
    return reputation.timeoutCounts();
  }

  void handleDisconnect() {
    headersRequestManager.close();
    bodiesRequestManager.close();
    receiptsRequestManager.close();
    disconnectCallbacks.forEach(callback -> callback.onDisconnect(this));
  }

  public void registerKnownBlock(final Hash hash) {
    knownBlocks.add(hash);
  }

  public void registerStatusSent() {
    statusHasBeenSentToPeer.set(true);
    maybeExecuteStatusesExchangedCallback();
  }

  public void registerStatusReceived(final Hash hash, final UInt256 td) {
    chainHeadState.statusReceived(hash, td);
    statusHasBeenReceivedFromPeer.set(true);
    maybeExecuteStatusesExchangedCallback();
  }

  private void maybeExecuteStatusesExchangedCallback() {
    if (readyForRequests()) {
      final Consumer<EthPeer> callback = onStatusesExchanged.getAndSet(null);
      if (callback == null) {
        return;
      }
      callback.accept(this);
    }
  }

  /**
   * Wait until status has been received and verified before using a peer.
   *
   * @return true if the peer is ready to accept requests for data.
   */
  public boolean readyForRequests() {
    return statusHasBeenSentToPeer.get() && statusHasBeenReceivedFromPeer.get();
  }

  /**
   * True if the peer has sent its initial status message to us.
   *
   * @return true if the peer has sent its initial status message to us.
   */
  public boolean statusHasBeenReceived() {
    return statusHasBeenReceivedFromPeer.get();
  }

  /** @return true if we have sent a status message to this peer. */
  public boolean statusHasBeenSentToPeer() {
    return statusHasBeenSentToPeer.get();
  }

  public boolean hasSeenBlock(final Hash hash) {
    return knownBlocks.contains(hash);
  }

  public ChainState chainState() {
    return chainHeadState;
  }

  public void registerHeight(final Hash blockHash, final long height) {
    chainHeadState.update(blockHash, height);
  }

  public int outstandingRequests() {
    return headersRequestManager.outstandingRequests()
        + bodiesRequestManager.outstandingRequests()
        + receiptsRequestManager.outstandingRequests();
  }

  public BytesValue nodeId() {
    return connection.getPeer().getNodeId();
  }

  @Override
  public String toString() {
    return nodeId().toString().substring(0, 20) + "...";
  }

  @FunctionalInterface
  public interface DisconnectCallback {
    void onDisconnect(EthPeer peer);
  }
}
