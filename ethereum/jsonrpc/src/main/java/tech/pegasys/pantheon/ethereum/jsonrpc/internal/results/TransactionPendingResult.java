package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
  "from",
  "gas",
  "gasPrice",
  "hash",
  "input",
  "nonce",
  "to",
  "transactionIndex",
  "value",
  "v",
  "r",
  "s"
})
public class TransactionPendingResult implements TransactionResult {

  private final String from;
  private final String gas;
  private final String gasPrice;
  private final String hash;
  private final String input;
  private final String nonce;
  private final String to;
  private final String value;
  private final String v;
  private final String r;
  private final String s;

  public TransactionPendingResult(final Transaction transaction) {
    this.from = transaction.getSender().toString();
    this.gas = Quantity.create(transaction.getGasLimit());
    this.gasPrice = Quantity.create(transaction.getGasPrice());
    this.hash = transaction.hash().toString();
    this.input = transaction.getPayload().toString();
    this.nonce = Quantity.create(transaction.getNonce());
    this.to = transaction.getTo().map(BytesValue::toString).orElse(null);
    this.value = Quantity.create(transaction.getValue());
    this.v = Quantity.format(transaction.getV());
    this.r = Quantity.format(transaction.getR());
    this.s = Quantity.format(transaction.getS());
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "gas")
  public String getGas() {
    return gas;
  }

  @JsonGetter(value = "gasPrice")
  public String getGasPrice() {
    return gasPrice;
  }

  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "input")
  public String getInput() {
    return input;
  }

  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "value")
  public String getValue() {
    return value;
  }

  @JsonGetter(value = "v")
  public String getV() {
    return v;
  }

  @JsonGetter(value = "r")
  public String getR() {
    return r;
  }

  @JsonGetter(value = "s")
  public String getS() {
    return s;
  }
}
