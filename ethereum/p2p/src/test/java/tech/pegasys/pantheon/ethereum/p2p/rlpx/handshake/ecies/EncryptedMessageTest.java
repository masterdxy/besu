package tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.ecies;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.concurrent.ThreadLocalRandom;

import org.assertj.core.api.Assertions;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.junit.Test;

/** Tests for {@link EncryptedMessage}. */
public final class EncryptedMessageTest {

  @Test
  public void eip8RoundTrip() throws InvalidCipherTextException {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final byte[] message = new byte[288];
    ThreadLocalRandom.current().nextBytes(message);
    final BytesValue initial = BytesValue.wrap(message);
    final BytesValue encrypted = EncryptedMessage.encryptMsgEip8(initial, keyPair.getPublicKey());
    final BytesValue decrypted =
        EncryptedMessage.decryptMsgEIP8(encrypted, keyPair.getPrivateKey());
    Assertions.assertThat(decrypted.slice(0, 288)).isEqualTo(initial);
  }
}
