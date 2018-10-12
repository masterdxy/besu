package tech.pegasys.pantheon.ethereum.p2p;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.pantheon.ethereum.p2p.NetworkingTestHelper.configWithRandomPorts;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryServiceException;
import tech.pegasys.pantheon.ethereum.p2p.netty.NettyP2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;

import java.io.IOException;

import io.vertx.core.Vertx;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

public class NetworkingServiceLifecycleTest {

  private final Vertx vertx = Vertx.vertx();

  @After
  public void closeVertx() {
    vertx.close();
  }

  @Test
  public void createPeerDiscoveryAgent() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    try (NettyP2PNetwork service =
        new NettyP2PNetwork(
            vertx,
            keyPair,
            configWithRandomPorts(),
            emptyList(),
            () -> true,
            new PeerBlacklist())) {
      service.run();
      final int port = service.getDiscoverySocketAddress().getPort();

      assertEquals("/0.0.0.0:" + port, service.getDiscoverySocketAddress().toString());
      assertThat(service.getDiscoveryPeers()).hasSize(0);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeerDiscoveryAgent_NullHost() throws IOException {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindHost(null));
    try (P2PNetwork broken =
        new NettyP2PNetwork(vertx, keyPair, config, emptyList(), () -> true, new PeerBlacklist())) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeerDiscoveryAgent_InvalidHost() throws IOException {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindHost("fake.fake.fake"));
    try (P2PNetwork broken =
        new NettyP2PNetwork(vertx, keyPair, config, emptyList(), () -> true, new PeerBlacklist())) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeerDiscoveryAgent_InvalidPort() throws IOException {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindPort(-1));
    try (P2PNetwork broken =
        new NettyP2PNetwork(vertx, keyPair, config, emptyList(), () -> true, new PeerBlacklist())) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeerDiscoveryAgent_NullKeyPair() throws IOException {
    try (P2PNetwork broken =
        new NettyP2PNetwork(
            vertx, null, configWithRandomPorts(), emptyList(), () -> true, new PeerBlacklist())) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test
  public void startStopPeerDiscoveryAgent() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    try (NettyP2PNetwork service =
        new NettyP2PNetwork(
            vertx,
            keyPair,
            configWithRandomPorts(),
            emptyList(),
            () -> true,
            new PeerBlacklist())) {
      service.run();
      service.stop();
      service.run();
    }
  }

  @Test
  public void startDiscoveryAgentBackToBack() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    try (NettyP2PNetwork service1 =
            new NettyP2PNetwork(
                vertx,
                keyPair,
                configWithRandomPorts(),
                emptyList(),
                () -> true,
                new PeerBlacklist());
        NettyP2PNetwork service2 =
            new NettyP2PNetwork(
                vertx,
                keyPair,
                configWithRandomPorts(),
                emptyList(),
                () -> true,
                new PeerBlacklist())) {
      service1.run();
      service1.stop();
      service2.run();
      service2.stop();
    }
  }

  @Test
  public void startDiscoveryPortInUse() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    try (NettyP2PNetwork service1 =
        new NettyP2PNetwork(
            vertx,
            keyPair,
            configWithRandomPorts(),
            emptyList(),
            () -> true,
            new PeerBlacklist())) {
      service1.run();
      final NetworkingConfiguration config = configWithRandomPorts();
      config.getDiscovery().setBindPort(service1.getDiscoverySocketAddress().getPort());
      try (NettyP2PNetwork service2 =
          new NettyP2PNetwork(
              vertx, keyPair, config, emptyList(), () -> true, new PeerBlacklist())) {
        try {
          service2.run();
        } catch (final Exception e) {
          assertThat(e.getCause()).hasCauseExactlyInstanceOf(PeerDiscoveryServiceException.class);
          assertThat(e.getCause())
              .hasMessageStartingWith(
                  "tech.pegasys.pantheon.ethereum.p2p.discovery."
                      + "PeerDiscoveryServiceException: Failed to bind Ethereum UDP discovery listener to 0.0.0.0:");
          assertThat(e).hasMessageContaining("Address already in use");
        } finally {
          service1.stop();
          service2.stop();
        }
      }
    }
  }

  @Test
  public void createPeerDiscoveryAgent_NoActivePeers() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    try (NettyP2PNetwork agent =
        new NettyP2PNetwork(
            vertx,
            keyPair,
            configWithRandomPorts(),
            emptyList(),
            () -> true,
            new PeerBlacklist())) {
      assertTrue(agent.getDiscoveryPeers().isEmpty());
      assertEquals(0, agent.getPeers().size());
    }
  }
}
