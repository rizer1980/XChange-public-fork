package info.bitrich.xchangestream.bybit;


import static java.math.RoundingMode.UP;
import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;
import org.knowm.xchange.bybit.service.BybitAccountService;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BybitStreamDemoAccount {

  private static final Logger log = LoggerFactory.getLogger(BybitStreamDemoAccount.class);

  public static void main(String[] args) {
    try {
      demoAccount();
      authDemoAccount();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  //  private static final Instrument ETH_PERP = new CurrencyPair("ETH/USDT/PERP");
  private static final Instrument ETH_PERP = new FuturesContract("ETH/USDT/PERP");
  static Ticker ticker;
  static BigDecimal amount;

  private static void demoAccount() throws IOException {
    ExchangeSpecification exchangeSpecification =
        new BybitStreamingExchange().getDefaultExchangeSpecification();
    exchangeSpecification.setApiKey(System.getProperty("test_api_key"));
    exchangeSpecification.setSecretKey(System.getProperty("test_secret_key"));
    exchangeSpecification.setExchangeSpecificParametersItem(SPECIFIC_PARAM_ACCOUNT_TYPE,
        BybitAccountType.UNIFIED);
    exchangeSpecification.setExchangeSpecificParametersItem(BybitStreamingExchange.EXCHANGE_TYPE,
        BybitCategory.LINEAR);
    exchangeSpecification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(
        exchangeSpecification);
    exchange.connect().blockingAwait();
    ticker = (exchange.getMarketDataService().getTicker(ETH_PERP));
    amount = exchange.getExchangeMetaData().getInstruments().get(ETH_PERP).getMinimumAmount();
    if (amount.multiply(ticker.getLast()).compareTo(new BigDecimal("5.0")) <= 0) {
      amount = new BigDecimal("5").divide(ticker.getAsk(),
          exchange.getExchangeMetaData().getInstruments().get(ETH_PERP).getVolumeScale(), UP);
    }
  }

  private static void authDemoAccount() throws IOException {
    ExchangeSpecification exchangeSpecification =
        new BybitStreamingExchange().getDefaultExchangeSpecification();
    exchangeSpecification.setApiKey(System.getProperty("test_api_key"));
    exchangeSpecification.setSecretKey(System.getProperty("test_secret_key"));
    exchangeSpecification.setExchangeSpecificParametersItem(SPECIFIC_PARAM_ACCOUNT_TYPE,
        BybitAccountType.UNIFIED);
    exchangeSpecification.setExchangeSpecificParametersItem(BybitStreamingExchange.EXCHANGE_TYPE,
        BybitCategory.LINEAR);
    exchangeSpecification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    StreamingExchange exchange = StreamingExchangeFactory.INSTANCE.createExchange(
        exchangeSpecification);
    exchange.connect().blockingAwait();
    List<Disposable> positionChangesDisposable = new ArrayList<>();
    BybitAccountService bybitAccountService = (BybitAccountService) exchange.getAccountService();
    log.info("switch mode to one-way, result {}", bybitAccountService.switchMode(BybitCategory.LINEAR, null,"USDT", 0));
    log.info("set leverage to 1.1, {}", bybitAccountService.setLeverage(BybitCategory.LINEAR,ETH_PERP, 1.1));
    positionChangesDisposable.add(
        ((BybitStreamingTradeService) exchange.getStreamingTradeService()).getBybitPositionChanges(
                BybitCategory.LINEAR)
            .doOnError(
                error -> {
                  log.error(error.getMessage());
                })
            .subscribe(p ->
                log.info("position change {}", p)));
    try {
      Thread.sleep(1000L);
      MarketOrder marketOrder = new MarketOrder(OrderType.BID,amount, ETH_PERP);
      log.info("market order id: {}",exchange.getTradeService().placeMarketOrder(marketOrder));
      Thread.sleep(10000L);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    for (Disposable disposable : positionChangesDisposable) {
      disposable.dispose();
    }

  }
}
