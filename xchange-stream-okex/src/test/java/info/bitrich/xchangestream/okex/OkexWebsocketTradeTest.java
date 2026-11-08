package info.bitrich.xchangestream.okex;

import static info.bitrich.xchangestream.okex.Utils.getMinAmount;
import static org.knowm.xchange.dto.Order.OrderType.*;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OrderHedgeMode;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.trade.OkexTradeParams;
import org.knowm.xchange.okex.service.OkexAccountService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled
class OkexWebsocketTradeTest {

  private static final Logger LOG = LoggerFactory.getLogger(OkexWebsocketTradeTest.class);
  StreamingExchange exchange;
  Instrument instrumentFutures = new FuturesContract("XRP/USDT/SWAP");
  private final boolean logOutput = true;
  private final boolean isDemoMode = false;

  @BeforeEach
  void setUp() {
    ExchangeSpecification spec = new OkexStreamingExchange().getDefaultExchangeSpecification();
    if (isDemoMode) {
      AuthUtils.setApiAndSecretKey(spec, "okx-demo");
      spec.setExchangeSpecificParametersItem(
          OkexExchange.PARAM_PASSPHRASE,
          AuthUtils.getSecretProperties("okx-demo").getProperty("passphrase"));
      // for xchange-stream demo
      spec.setExchangeSpecificParametersItem(OkexExchange.USE_SANDBOX, true);
      // for REST demo
      spec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, "1");
    } else {
      AuthUtils.setApiAndSecretKey(spec, "okx");
      spec.setExchangeSpecificParametersItem(
          OkexExchange.PARAM_PASSPHRASE,
          AuthUtils.getSecretProperties("okx").getProperty("passphrase"));
    }
    exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(OkexStreamingExchange.class);
    exchange.applySpecification(spec);
    exchange.connect().blockingAwait();
    // OPTION - wait for login message response
    while (!exchange.isAlive()) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  void websocketFuturesTradeTest() throws Exception {
    tradeTest(instrumentFutures);
  }

  @Test
  void websocketSpotTradeTest() throws Exception {
    tradeTest(new CurrencyPair("SOL/USDT"));
  }

  @Test
  void websocketHedgeModeTradeTest() throws Exception {
    // switch mode to two-way
    ((OkexAccountService) exchange.getAccountService()).setPositionDualModeRaw(true);
    Ticker ticker = exchange.getMarketDataService().getTicker(instrumentFutures);
    BigDecimal minAmount =
        exchange.getExchangeMetaData().getInstruments().get(instrumentFutures).getMinimumAmount();
    minAmount =
        getMinAmount(
            new BigDecimal("5"),
            minAmount,
            ticker,
            exchange
                .getExchangeMetaData()
                .getInstruments()
                .get(instrumentFutures)
                .getVolumeScale());
    String limitOrderAskUserId = RandomStringUtils.randomAlphanumeric(20);
    LimitOrder limitOrderAsk =
        new LimitOrder.Builder(Order.OrderType.ASK, instrumentFutures)
            .originalAmount(minAmount)
            .limitPrice(ticker.getHigh())
            .userReference(limitOrderAskUserId)
            .flag(OrderHedgeMode.TWOWAY)
            .build();
    Disposable limitOrderAskDisposable =
        exchange
            .getStreamingTradeService()
            .placeLimitOrder(limitOrderAsk)
            .subscribe(
                result -> {
                  LOG.info("limitOrderAsk is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("limitOrderAsk is disposed: {}", limitOrderAskDisposable.isDisposed());
    String limitOrderBidUserId = RandomStringUtils.randomAlphanumeric(20);
    LimitOrder limitOrderBid =
        new LimitOrder.Builder(Order.OrderType.BID, instrumentFutures)
            .originalAmount(minAmount)
            .limitPrice(ticker.getLow())
            .userReference(limitOrderBidUserId)
            .flag(OrderHedgeMode.TWOWAY)
            .build();
    Disposable limitOrderBidDisposable =
        exchange
            .getStreamingTradeService()
            .placeLimitOrder(limitOrderBid)
            .subscribe(
                result -> {
                  LOG.info("limitOrderBid is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("limitOrderBid is disposed: {}", limitOrderBidDisposable.isDisposed());
    Disposable cancelLimitOrderAskDisposable =
        exchange
            .getStreamingTradeService()
            .cancelOrder(
                new OkexTradeParams.OkexCancelOrderParams(
                    instrumentFutures, null, limitOrderAskUserId))
            .subscribe(
                result -> {
                  LOG.info("cancelLimitOrderAsk is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("cancelLimitOrderAsk is disposed: {}", cancelLimitOrderAskDisposable.isDisposed());
    Disposable cancelLimitOrderBidDisposable =
        exchange
            .getStreamingTradeService()
            .cancelOrder(
                new OkexTradeParams.OkexCancelOrderParams(
                    instrumentFutures, null, limitOrderBidUserId))
            .subscribe(
                result -> {
                  LOG.info("cancelLimitOrderBid is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("cancelLimitOrderBid is disposed: {}", cancelLimitOrderBidDisposable.isDisposed());
    MarketOrder marketOrderAsk =
        new MarketOrder.Builder(Order.OrderType.ASK, instrumentFutures)
            .originalAmount(minAmount)
            .flag(OrderHedgeMode.TWOWAY)
            .build();
    Disposable marketOrderAskDisposable =
        exchange
            .getStreamingTradeService()
            .placeMarketOrder(marketOrderAsk)
            .subscribe(
                result -> {
                  LOG.info("marketOrderAsk is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("marketOrderAsk is disposed: {}", marketOrderAskDisposable.isDisposed());

    MarketOrder marketOrderBid =
        new MarketOrder.Builder(Order.OrderType.BID, instrumentFutures)
            .originalAmount(minAmount)
            .flag(OrderHedgeMode.TWOWAY)
            .build();
    Disposable marketOrderBidDisposable =
        exchange
            .getStreamingTradeService()
            .placeMarketOrder(marketOrderBid)
            .subscribe(
                result -> {
                  LOG.info("marketOrderBid is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("marketOrderBid is disposed: {}", marketOrderBidDisposable.isDisposed());

    MarketOrder marketOrderCloseAsk =
        new MarketOrder.Builder(Order.OrderType.EXIT_ASK, instrumentFutures)
            .originalAmount(minAmount)
            .flag(OrderHedgeMode.TWOWAY)
            .build();
    Disposable marketOrderCloseAskDisposable =
        exchange
            .getStreamingTradeService()
            .placeMarketOrder(marketOrderCloseAsk)
            .subscribe(
                result -> {
                  LOG.info("marketOrderCloseAsk is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("marketOrderCloseAsk is disposed: {}", marketOrderCloseAskDisposable.isDisposed());

    MarketOrder marketOrderCloseBid =
        new MarketOrder.Builder(Order.OrderType.EXIT_BID, instrumentFutures)
            .originalAmount(minAmount)
            .flag(OrderHedgeMode.TWOWAY)
            .build();
    Disposable marketOrderCloseBidDisposable =
        exchange
            .getStreamingTradeService()
            .placeMarketOrder(marketOrderCloseBid)
            .subscribe(
                result -> {
                  LOG.info("marketOrderCloseBid is send, retCode: {}", result);
                },
                throwable -> LOG.error("throwable", throwable));
    Thread.sleep(1000);
    LOG.info("marketOrderCloseBid is disposed: {}", marketOrderCloseBidDisposable.isDisposed());
    // switch back to one-way
    ((OkexAccountService) exchange.getAccountService()).setPositionDualModeRaw(false);
  }

  private void tradeTest(Instrument instrument) throws IOException, InterruptedException {
    StreamingTradeService tradeService = exchange.getStreamingTradeService();
    Ticker ticker = exchange.getMarketDataService().getTicker(instrument);
    BigDecimal minAmount =
        exchange.getExchangeMetaData().getInstruments().get(instrument).getMinimumAmount();
    BigDecimal amount =
        getMinAmount(
            new BigDecimal("10"),
            minAmount,
            ticker,
            exchange.getExchangeMetaData().getInstruments().get(instrument).getVolumeScale());
    AtomicReference<BigDecimal> amountToSell = new AtomicReference<>(BigDecimal.ZERO);
    String limitOrderUserId = RandomStringUtils.randomAlphanumeric(20);
    String marketOrderBidUserId = RandomStringUtils.randomAlphanumeric(20);
    Disposable disposableOrderChange =
        exchange
            .getStreamingTradeService()
            .getOrderChanges(instrument)
            .subscribe(
                result -> {
                  if (result.getUserReference().equals(marketOrderBidUserId)) {
                    amountToSell.set(result.getCumulativeAmount());
                  }
                  if (logOutput) {
                    LOG.info("orderChange result: {}", result);
                  }
                },
                throwable -> LOG.error("orderChange error", throwable));
    Thread.sleep(1000);
    LimitOrder limitOrder =
        new LimitOrder.Builder(BID, instrument)
            .limitPrice(ticker.getLow())
            .originalAmount(amount)
            .userReference(limitOrderUserId)
            .build();
    limitOrder.addOrderFlag(OrderHedgeMode.TWOWAY);
    Disposable placeLimitOrderDisposable =
        tradeService
            .placeLimitOrder(limitOrder)
            .subscribe(
                result -> {
                  if (logOutput) {
                    LOG.info("placeLimitOrder result: {}", result.toString());
                  }
                },
                throwable -> LOG.error("placeLimitOrder error", throwable));
    Thread.sleep(1000);
    if (logOutput) {
      LOG.info("placeLimitOrderDisposable disposed: {}", placeLimitOrderDisposable.isDisposed());
    }
    LimitOrder limitOrderChange =
        new LimitOrder.Builder(BID, instrument)
            .limitPrice(ticker.getLow().add(BigDecimal.ONE.negate()))
            .userReference(limitOrderUserId)
            .originalAmount(amount)
            .build();
    limitOrder.addOrderFlag(OrderHedgeMode.TWOWAY);
    Disposable placeLimitOrderChangeDisposable =
        tradeService
            .changeOrder(limitOrderChange)
            .subscribe(
                result -> {
                  if (logOutput) {
                    LOG.info("changeOrder result: {}", result.toString());
                  }
                },
                throwable -> LOG.error("changeOrder error", throwable));
    Thread.sleep(1000);
    if (logOutput) {
      LOG.info("changeOrder disposed: {}", placeLimitOrderChangeDisposable.isDisposed());
    }
    CancelOrderParams params =
        new OkexTradeParams.OkexCancelOrderParams(instrument, null, limitOrderUserId);
    Disposable cancelOrderDisposable =
        tradeService
            .cancelOrder(params)
            .subscribe(
                result -> {
                  if (logOutput) {
                    LOG.info("cancelOrder result: {}", result.toString());
                  }
                },
                throwable -> LOG.error("cancelOrder error", throwable));
    Thread.sleep(1000);
    if (logOutput) {
      LOG.info("cancelOrderDisposable disposed: {}", cancelOrderDisposable.isDisposed());
    }
    MarketOrder marketOrderBid =
        new MarketOrder.Builder(BID, instrument)
            .userReference(marketOrderBidUserId)
            .originalAmount(amount)
            .build();
    Disposable marketOrderBidDisposable =
        tradeService
            .placeMarketOrder(marketOrderBid)
            .subscribe(
                result -> {
                  if (logOutput) {
                    LOG.info("marketOrderBid result: {}", result.toString());
                  }
                },
                throwable -> LOG.error("marketOrderBid error", throwable));
    Thread.sleep(1000);
    if (logOutput) {
      LOG.info("marketOrderBidDisposable disposed: {}", marketOrderBidDisposable.isDisposed());
    }
    String marketOrderAskUserId = RandomStringUtils.randomAlphanumeric(20);
    MarketOrder marketOrderAsk =
        new MarketOrder.Builder(EXIT_BID, instrument)
            .userReference(marketOrderAskUserId)
            .originalAmount(amountToSell.get())
            .build();
    Disposable marketOrderAskDisposable =
        tradeService
            .placeMarketOrder(marketOrderAsk)
            .subscribe(
                result -> {
                  if (logOutput) {
                    LOG.info("marketOrderAsk result: {}", result.toString());
                  }
                },
                throwable -> LOG.error("marketOrderAsk error", throwable));
    Thread.sleep(1000);
    if (logOutput) {
      LOG.info("marketOrderAskDisposable disposed: {}", marketOrderAskDisposable.isDisposed());
    }
  }
}
