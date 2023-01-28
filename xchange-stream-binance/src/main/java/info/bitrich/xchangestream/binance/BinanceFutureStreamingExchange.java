package info.bitrich.xchangestream.binance;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import info.bitrich.xchangestream.util.Events;
import io.reactivex.Completable;
import io.reactivex.Observable;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.binance.BinanceFutureAuthenticated;
import org.knowm.xchange.binance.BinanceFutureExchange;
import org.knowm.xchange.binance.service.BinanceFutureMarketDataService;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;

public class BinanceFutureStreamingExchange extends BinanceFutureExchange implements StreamingExchange {
  private static final Logger LOG = LoggerFactory.getLogger(BinanceFutureStreamingExchange.class);
  //    private static final String WS_API_BASE_URI = "wss://fstream-auth.binance.com/";
  private static final String WS_API_BASE_URI = "wss://fstream.binance.com/";
  private static final String WS_SANDBOX_API_BASE_URI = "wss://stream.binancefuture.com";
  public static final String USE_HIGHER_UPDATE_FREQUENCY = "Binance_Orderbook_Use_Higher_Frequency";
  public static final String USE_REALTIME_BOOK_TICKER = "Binance_Ticker_Use_Realtime";
  public static final String FETCH_ORDER_BOOK_LIMIT = "Binance_Fetch_Order_Book_Limit";

  private BinanceStreamingService streamingService;
  private BinanceFutureUserDataChannel userDataChannel;
  private BinanceUserDataStreamingService userDataStreamingService;

  private BinanceFutureStreamingMarketDataService streamingMarketDataService;
  private BinanceStreamingAccountService streamingAccountService;
  private BinanceStreamingTradeService streamingTradeService;

  Runnable onApiCall;
  boolean realtimeOrderBookTicker;
  String orderBookUpdateFrequencyParameter = "";
  int oderBookFetchLimitParameter = 1000;

  @Override
  protected void initServices() {
    super.initServices();
    this.onApiCall = Events.onApiCall(exchangeSpecification);
    boolean userHigherFrequency = Boolean.TRUE.equals(exchangeSpecification.getExchangeSpecificParametersItem(USE_HIGHER_UPDATE_FREQUENCY));
    realtimeOrderBookTicker = Boolean.TRUE.equals(exchangeSpecification.getExchangeSpecificParametersItem(USE_REALTIME_BOOK_TICKER));
    if (userHigherFrequency) {
      orderBookUpdateFrequencyParameter = "@100ms";
    }
    Object fetchOrderBookLimit = exchangeSpecification.getExchangeSpecificParametersItem(FETCH_ORDER_BOOK_LIMIT);
    if (fetchOrderBookLimit instanceof Integer) {
      oderBookFetchLimitParameter = (int) fetchOrderBookLimit;
    }
  }

  public Completable connect(KlineSubscription klineSubscription, ProductSubscription... args) {
    if (klineSubscription == null || klineSubscription.isEmpty()) {
      return connect(args);
    }
    if (args == null || args.length == 0) {
      return internalConnect(klineSubscription, ProductSubscription.create().build());
    }
    return internalConnect(klineSubscription, args);
  }

  /**
   * Binance streaming API expects connections to multiple channels to be defined at connection
   * time. To define the channels for this connection pass a `ProductSubscription` in at connection
   * time.
   *
   * @param args A single `ProductSubscription` to define the subscriptions required to be available
   *             during this connection.
   * @return A completable which fulfils once connection is complete.
   */
  @Override
  public Completable connect(ProductSubscription... args) {
    if (args == null || args.length == 0) {
      throw new IllegalArgumentException("Subscriptions must be made at connection time");
    }
    return internalConnect(new KlineSubscription(emptyMap()), args);
  }

  private Completable internalConnect(KlineSubscription klineSubscription, ProductSubscription... args) {
    if (streamingService != null) {
      throw new UnsupportedOperationException("Exchange only handles a single connection - disconnect the current connection.");
    }

    ProductSubscription subscriptions = args[0];
    streamingService = createStreamingService(subscriptions, klineSubscription);

    List<Completable> completables = new ArrayList<>();

    if (subscriptions.hasUnauthenticated() || klineSubscription.hasUnauthenticated()) {
      completables.add(streamingService.connect());
    }

    if (subscriptions.hasAuthenticated()) {
      if (exchangeSpecification.getApiKey() == null) {
        throw new IllegalArgumentException("API key required for authenticated streams");
      }

      LOG.info("Connecting to authenticated web socket");
      BinanceFutureAuthenticated binance = ExchangeRestProxyBuilder.forInterface(BinanceFutureAuthenticated.class,
              getExchangeSpecification()).build();
      userDataChannel = new BinanceFutureUserDataChannel(binance, exchangeSpecification.getApiKey(), onApiCall);
      try {
        completables.add(createAndConnectUserDataService(userDataChannel.getListenKey()));
      } catch (BinanceFutureUserDataChannel.NoActiveChannelException e) {
        throw new IllegalStateException("Failed to establish user data channel", e);
      }
    }

    streamingMarketDataService = new BinanceFutureStreamingMarketDataService(streamingService,
            (BinanceFutureMarketDataService) marketDataService, onApiCall, orderBookUpdateFrequencyParameter, realtimeOrderBookTicker,
            oderBookFetchLimitParameter);
    streamingAccountService = new BinanceStreamingAccountService(userDataStreamingService);
    streamingTradeService = new BinanceStreamingTradeService(userDataStreamingService);

    return Completable.concat(completables)
            .doOnComplete(() -> streamingMarketDataService.openSubscriptions(subscriptions, klineSubscription))
            .doOnComplete(() -> streamingAccountService.openSubscriptions()).doOnComplete(() -> streamingTradeService.openSubscriptions());
  }

  private Completable createAndConnectUserDataService(String listenKey) {
    userDataStreamingService = BinanceUserDataStreamingService.create(getStreamingBaseUri(), listenKey);
    applyStreamingSpecification(getExchangeSpecification(), userDataStreamingService);
    return userDataStreamingService.connect().doOnComplete(() -> {
      LOG.info("Connected to authenticated web socket");
      userDataChannel.onChangeListenKey(newListenKey -> userDataStreamingService.disconnect().doOnComplete(() -> {
        createAndConnectUserDataService(newListenKey).doOnComplete(() -> {
          streamingAccountService.setUserDataStreamingService(userDataStreamingService);
          streamingTradeService.setUserDataStreamingService(userDataStreamingService);
        });
      }));
    });
  }

  @Override
  public Completable disconnect() {
    List<Completable> completables = new ArrayList<>();
    //    if (streamingService != null) {
    completables.add(streamingService.disconnect());
    streamingService = null;
    //    }
    if (userDataStreamingService != null) {
      completables.add(userDataStreamingService.disconnect());
      userDataStreamingService = null;
    }
    if (userDataChannel != null) {
      userDataChannel.close();
      userDataChannel = null;
    }
    return Completable.concat(completables);
  }

  @Override
  public boolean isAlive() {
    return streamingService != null && streamingService.isSocketOpen();
  }

  @Override
  public Observable<Throwable> reconnectFailure() {
    return streamingService.subscribeReconnectFailure();
  }

  @Override
  public Observable<Object> connectionSuccess() {
    return streamingService.subscribeConnectionSuccess();
  }

  @Override
  public Observable<State> connectionStateObservable() {
    return streamingService.subscribeConnectionState();
  }

  @Override
  public BinanceFutureStreamingMarketDataService getStreamingMarketDataService() {
    return streamingMarketDataService;
  }

  @Override
  public BinanceStreamingAccountService getStreamingAccountService() {
    return streamingAccountService;
  }

  @Override
  public BinanceStreamingTradeService getStreamingTradeService() {
    return streamingTradeService;
  }

  protected BinanceStreamingService createStreamingService(ProductSubscription subscription, KlineSubscription klineSubscription) {
    String path = getStreamingBaseUri() + "stream?streams=" + buildSubscriptionStreams(subscription, klineSubscription);
    BinanceStreamingService streamingService = new BinanceStreamingService(path, subscription, klineSubscription);
    applyStreamingSpecification(getExchangeSpecification(), streamingService);
    return streamingService;
  }

  private String buildSubscriptionStreams(ProductSubscription subscription, KlineSubscription klineSubscription) {
    return Stream.concat(Arrays.stream(buildSubscriptionStreams(subscription).split("/")), buildSubscriptionStreams(klineSubscription))
            .filter(StringUtils::isNotEmpty).collect(Collectors.joining("/"));
  }

  private Stream<String> buildSubscriptionStreams(KlineSubscription klineSubscription) {
    return klineSubscription.getKlines().entrySet().stream()
            .flatMap(entry -> entry.getValue().stream().map(interval -> getPrefix(entry.getKey()) + "@kline_" + interval.code()));
  }

  protected String getStreamingBaseUri() {
    return Boolean.TRUE.equals(
            exchangeSpecification.getExchangeSpecificParametersItem(USE_SANDBOX)) ? WS_SANDBOX_API_BASE_URI : WS_API_BASE_URI;
  }

  public String buildSubscriptionStreams(ProductSubscription subscription) {
    return Stream.of(buildSubscriptionStrings(subscription.getTicker(),
                            realtimeOrderBookTicker ? BinanceSubscriptionType.BOOK_TICKER.getType() : BinanceSubscriptionType.TICKER.getType()),
                    buildSubscriptionStrings(subscription.getOrderBook(), BinanceSubscriptionType.DEPTH.getType()),
                    buildSubscriptionStrings(subscription.getTrades(), BinanceSubscriptionType.TRADE.getType())).filter(s -> !s.isEmpty())
            .collect(Collectors.joining("/"));
  }

  private String buildSubscriptionStrings(List<Instrument> currencyPairs, String subscriptionType) {
    if (BinanceSubscriptionType.DEPTH.getType().equals(subscriptionType)) {
      return subscriptionStrings(currencyPairs).map(s -> s + "@" + subscriptionType + orderBookUpdateFrequencyParameter)
              .collect(Collectors.joining("/"));
    } else {
      return subscriptionStrings(currencyPairs).map(s -> s + "@" + subscriptionType).collect(Collectors.joining("/"));
    }
  }

  private static Stream<String> subscriptionStrings(List<Instrument> currencyPairs) {
    return currencyPairs.stream().map(BinanceFutureStreamingExchange::getPrefix);
  }

  private static String getPrefix(Instrument pair) {
    return String.join("", pair.toString().split("/")).toLowerCase();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    streamingService.useCompressedMessages(compressedMessages);
  }

  public void enableLiveSubscription() {
    if (this.streamingService == null) {
      throw new UnsupportedOperationException("You must connect to streams before enabling live subscription.");
    }
    this.streamingService.enableLiveSubscription();
  }

  public void disableLiveSubscription() {
    if (this.streamingService != null) this.streamingService.disableLiveSubscription();
  }

  /**
   * Enables the user to listen on channel inactive events and react appropriately.
   *
   * @param channelInactiveHandler a WebSocketMessageHandler instance.
   */
  public void setChannelInactiveHandler(WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler) {
    streamingService.setChannelInactiveHandler(channelInactiveHandler);
  }
}