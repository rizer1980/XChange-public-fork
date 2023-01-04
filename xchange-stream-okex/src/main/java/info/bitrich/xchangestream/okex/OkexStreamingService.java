package info.bitrich.xchangestream.okex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.okex.dto.OkexLoginMessage;
import info.bitrich.xchangestream.okex.dto.OkexSubscribeMessage;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.BaseParamsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkexStreamingService extends JsonNettyStreamingService {

  private static final Logger LOG = LoggerFactory.getLogger(OkexStreamingService.class);
  private static final String LOGIN_SIGN_METHOD = "GET";
  private static final String LOGIN_SIGN_REQUEST_PATH = "/users/self/verify";

  private final Observable<Long> pingPongSrc = Observable.interval(15, 15, TimeUnit.SECONDS);
  private WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler = null;
  private Disposable pingPongSubscription;

  private final ExchangeSpecification xSpec;

  public OkexStreamingService(String apiUrl, ExchangeSpecification exchangeSpecification) {
    super(apiUrl);
    this.xSpec = exchangeSpecification;
  }

  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(
      WebSocketClientHandshaker handshake, WebSocketClientHandler.WebSocketMessageHandler handler) {
    LOG.info("Registering OkxWebSocketClientHandler");
    return new OkxWebSocketClientHandler(handshake, handler);
  }

  public void setChannelInactiveHandler(
      WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler) {
    this.channelInactiveHandler = channelInactiveHandler;
  }

  /**
   * Custom client handler in order to execute an external, user-provided handler on channel events.
   */
  class OkxWebSocketClientHandler extends NettyWebSocketClientHandler {

    public OkxWebSocketClientHandler(
        WebSocketClientHandshaker handshake, WebSocketMessageHandler handler) {
      super(handshake, handler);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      super.channelInactive(ctx);
      if (channelInactiveHandler != null) {
        channelInactiveHandler.onMessage("WebSocket Client disconnected!");
        LOG.info("channelInactive catch");
      }
    }
  }

  @Override
  public Completable connect() {
    Completable conn = super.connect();
    return conn.andThen(
        (CompletableSource)
            (completable) -> {
              try {
                if (xSpec.getApiKey() != null) {
                  login();
                }
                pingPongDisconnectIfConnected();

                //                pingPongSubscription = pingPongSrc.subscribe(o ->
                // this.sendMessage("ping"));
                completable.onComplete();
              } catch (Exception e) {
                completable.onError(e);
              }
            });
  }

  public void login() throws JsonProcessingException {
    Mac mac;
    try {
      mac = Mac.getInstance(BaseParamsDigest.HMAC_SHA_256);
      final SecretKey secretKey =
          new SecretKeySpec(
              xSpec.getSecretKey().getBytes(StandardCharsets.UTF_8), BaseParamsDigest.HMAC_SHA_256);
      mac.init(secretKey);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new ExchangeException("Invalid API secret", e);
    }
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String toSign = timestamp + LOGIN_SIGN_METHOD + LOGIN_SIGN_REQUEST_PATH;
    String sign =
        Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));

    OkexLoginMessage message = new OkexLoginMessage();
    String passphrase = (String) xSpec.getExchangeSpecificParametersItem("passphrase");
    OkexLoginMessage.LoginArg loginArg =
        new OkexLoginMessage.LoginArg(xSpec.getApiKey(), passphrase, timestamp, sign);
    message.getArgs().add(loginArg);

    this.sendMessage(objectMapper.writeValueAsString(message));
  }

  @Override
  public void messageHandler(String message) {
    LOG.debug("Received message: {}", message);
    JsonNode jsonNode;

    // Parse incoming message to JSON
    try {
      jsonNode = objectMapper.readTree(message);
    } catch (IOException e) {
      if ("pong".equals(message)) {
        // ping pong message

        return;
      }
      LOG.error("Error parsing incoming message to JSON: {}", message);
      return;
    }
    LOG.debug("messageHandler {} ", message);
    if (processArrayMessageSeparately() && jsonNode.isArray()) {
      // In case of array - handle every message separately.
      for (JsonNode node : jsonNode) {
        handleMessage(node);
      }
    } else {
      handleMessage(jsonNode);
    }
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    if (message.has("event")) {
      String event = message.get("event").asText();
      if (event.equals("subscribe"))
        LOG.info(
            "Stream {} has been successfully subscribed to channel {}",
            message.get("arg").get("instId").asText(),
            message.get("arg").get("channel").asText());
      if (event.equals("error"))
        LOG.info(
            "Subscribe error code: {}, msg: {}",
            message.get("code").asText(),
            message.get("msg").asText());
      if (event.equals("unsubscribe"))
        LOG.info(
            "Stream {} has been successfully unsubscribed from channel {}",
            message.get("arg").get("instId").asText(),
            message.get("arg").get("channel").asText());
      return "";
    }
    JsonNode node = message.get("arg");
    return node.get("instId").asText() + "-" + node.get("channel").asText();
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    if (args.length != 1) throw new IOException("SubscribeMessage: Insufficient arguments");
    OkexSubscribeMessage.SubscriptionTopic topic =
        new OkexSubscribeMessage.SubscriptionTopic(args[0].toString(), null, null, channelName);
    OkexSubscribeMessage osm = new OkexSubscribeMessage();
    osm.setOp("subscribe");
    osm.getArgs().add(topic);
    return objectMapper.writeValueAsString(osm);
  }

  @Override
  public String getUnsubscribeMessage(String channelName, Object... args) throws IOException {
    LOG.info("channelName {} ", channelName);
    String subscriptionType = channelName.substring(channelName.lastIndexOf("-") + 1);
    LOG.info("subscriptionType {} ", subscriptionType);
    String instId = channelName.substring(0, channelName.lastIndexOf("-"));
    LOG.info("instId {} ", instId);
    OkexSubscribeMessage.SubscriptionTopic topic =
        new OkexSubscribeMessage.SubscriptionTopic(subscriptionType, null, null, instId);
    OkexSubscribeMessage message = new OkexSubscribeMessage();
    message.setOp("unsubscribe");
    message.getArgs().add(topic);
    //    if (subscriptionType.equals("books") | subscriptionType.equals("books5") |
    // |subscriptionType.equals("bbo-tbt"))
    //      orderBookMap.remove(instId);
    //    if (args.length != 1) throw new IOException("UnsubscribeMessage: Insufficient arguments");
    return objectMapper.writeValueAsString(message);
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    return channelName + "-" + args[0];
  }

  public void pingPongDisconnectIfConnected() {
    if (pingPongSubscription != null && !pingPongSubscription.isDisposed()) {
      pingPongSubscription.dispose();
      LOG.info("pingPongSubscription.dispose()");
    }
  }
}
