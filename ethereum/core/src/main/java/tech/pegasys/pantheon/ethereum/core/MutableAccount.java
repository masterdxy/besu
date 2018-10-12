package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Map;

/** A mutable world state account. */
public interface MutableAccount extends Account {

  /**
   * Increments (by 1) the nonce of this account.
   *
   * @return the previous value of the nonce.
   */
  default long incrementNonce() {
    final long current = getNonce();
    setNonce(current + 1);
    return current;
  }

  /**
   * Sets the nonce of this account to the provide value.
   *
   * @param value the value to set the nonce to.
   */
  void setNonce(long value);

  /**
   * Increments the account balance by the provided amount.
   *
   * @param value The amount to increment
   * @return the previous balance (before increment).
   */
  default Wei incrementBalance(final Wei value) {
    final Wei current = getBalance();
    setBalance(current.plus(value));
    return current;
  }

  /**
   * Decrements the account balance by the provided amount.
   *
   * @param value The amount to decrement
   * @return the previous balance (before decrement). The account must have enough funds or an
   *     exception is thrown.
   * @throws IllegalStateException if the account balance is strictly less than {@code value}.
   */
  default Wei decrementBalance(final Wei value) {
    final Wei current = getBalance();
    if (current.compareTo(value) < 0) {
      throw new IllegalStateException(
          String.format("Cannot remove %s wei from account, balance is only %s", value, current));
    }
    setBalance(current.minus(value));
    return current;
  }

  /**
   * Sets the balance of the account to the provided amount.
   *
   * @param value the amount to set.
   */
  void setBalance(Wei value);

  /**
   * Sets the code for the account.
   *
   * @param code the code to set for the account.
   */
  void setCode(BytesValue code);

  /**
   * Sets a particular key-value pair in the account storage.
   *
   * <p>Note that setting the value of an entry to 0 is basically equivalent to deleting that entry.
   *
   * @param key the key to set.
   * @param value the value to set {@code key} to.
   */
  void setStorageValue(UInt256 key, UInt256 value);

  /**
   * Returns the storage entries that have been set through the updater this instance came from.
   *
   * @return a map of storage that has been modified.
   */
  Map<UInt256, UInt256> getUpdatedStorage();
}
