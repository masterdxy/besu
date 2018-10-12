package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.AbstractEthJsonRpcHttpServiceTest;

import java.io.IOException;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class EthJsonRpcHttpServiceTest extends AbstractEthJsonRpcHttpServiceTest {

  private Hash recordPendingTransaction(final int blockNumber, final int transactionIndex) {
    final Block block = BLOCKS.get(1);
    final Transaction transaction = block.getBody().getTransactions().get(0);
    filterManager.recordPendingTransactionEvent(transaction);
    return transaction.hash();
  }

  @Test
  public void getFilterChanges_noBlocks() throws Exception {
    final String expectedRespBody =
        String.format("{%n  \"jsonrpc\" : \"2.0\",%n  \"id\" : 2,%n  \"result\" : [ ]%n}");
    final ResponseBody body = ethNewBlockFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Response resp = ethGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void getFilterChanges_oneBlock() throws Exception {
    final String expectedRespBody =
        String.format(
            "{%n  \"jsonrpc\" : \"2.0\",%n  \"id\" : 2,%n  \"result\" : [ \"0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9\" ]%n}");
    final ResponseBody body = ethNewBlockFilter(1).body();
    final String result = getResult(body);
    body.close();

    importBlock(1);
    final Response resp = ethGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void getFilterChanges_noTransactions() throws Exception {
    final String expectedRespBody =
        String.format("{%n  \"jsonrpc\" : \"2.0\",%n  \"id\" : 2,%n  \"result\" : [ ]%n}");
    final ResponseBody body = ethNewPendingTransactionFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Response resp = ethGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void getFilterChanges_oneTransaction() throws Exception {
    final ResponseBody body = ethNewPendingTransactionFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Hash transactionHash = recordPendingTransaction(1, 1);

    final Response resp = ethGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    final String expectedRespBody =
        String.format(
            "{%n  \"jsonrpc\" : \"2.0\",%n  \"id\" : 2,%n  \"result\" : [ \""
                + transactionHash
                + "\" ]%n}");
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void uninstallFilter() throws Exception {
    final String expectedRespBody =
        String.format("{%n  \"jsonrpc\" : \"2.0\",%n  \"id\" : 2,%n  \"result\" : true%n}");
    final ResponseBody body = ethNewBlockFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Response resp = ethUninstallFilter(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  private String getResult(final ResponseBody body) throws IOException {
    final JsonObject json = new JsonObject(body.string());
    return json.getString("result");
  }

  private Response jsonRpcRequest(final int id, final String method, final String params)
      throws Exception {
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"params\": "
                + params
                + ",\"method\":\""
                + method
                + "\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl).build();
    return client.newCall(request).execute();
  }

  private Response ethNewBlockFilter(final int id) throws Exception {
    return jsonRpcRequest(id, "eth_newBlockFilter", "[]");
  }

  private Response ethNewPendingTransactionFilter(final int id) throws Exception {
    return jsonRpcRequest(id, "eth_newPendingTransactionFilter", "[]");
  }

  private Response ethGetFilterChanges(final int id, final String filterId) throws Exception {
    return jsonRpcRequest(id, "eth_getFilterChanges", "[\"" + filterId + "\"]");
  }

  private Response ethUninstallFilter(final int id, final String filterId) throws Exception {
    return jsonRpcRequest(id, "eth_uninstallFilter", "[\"" + filterId + "\"]");
  }
}
