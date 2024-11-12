package info.bitrich.xchangestream.binance;

import static org.knowm.xchange.binance.BinanceExchange.EXCHANGE_TYPE;
import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;

import info.bitrich.xchangestream.binancefuture.BinanceFutureStreamingExchange;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.rxjava3.disposables.Disposable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadLeakTest {

  static Instrument instrument = new FuturesContract("BTC/USDT/PERP");
  private static final Logger log = LoggerFactory.getLogger(ThreadLeakTest.class);

  public static void main(String[] args) throws InterruptedException {
    final ExchangeSpecification exchangeSpecification =
        new ExchangeSpecification(BinanceFutureStreamingExchange.class);
    exchangeSpecification.setShouldLoadRemoteMetaData(true);
    exchangeSpecification.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.binance.BinanceStreamingExchange.USE_REALTIME_BOOK_TICKER, true);
    exchangeSpecification.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.binance.BinanceStreamingExchange.USE_HIGHER_UPDATE_FREQUENCY,
        true);
    exchangeSpecification.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.binance.BinanceStreamingExchange.FETCH_ORDER_BOOK_LIMIT, 500);
    exchangeSpecification.setExchangeSpecificParametersItem(EXCHANGE_TYPE, FUTURES);
    BinanceStreamingExchange exchange =
        (BinanceStreamingExchange)
            StreamingExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    BinanceFutureStreamingExchange binanceExchange = (BinanceFutureStreamingExchange) exchange;
    ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
    builder.addOrderbook(instrument);

    ProductSubscription subscription = builder.build();
    binanceExchange.connect(subscription).blockingAwait();
    binanceExchange.enableLiveSubscription();
    Disposable booksDisposable =
        binanceExchange
            .getStreamingMarketDataService()
            .getOrderBook(instrument)
            .doOnDispose(
                () -> {
                  binanceExchange.getStreamingMarketDataService().unsubscribe(instrument,
                      BinanceSubscriptionType.DEPTH);
                  log.info("Binance Future unsubscribe pair {}", instrument);
                })
            .subscribe(orderBook
                    -> {
                  log.info("orderbook {}", orderBook.getTimeStamp());
                }, throwable -> {
                  log.error(
                      "Binance Future throwable encoutered error while subscribing OrderBook to pair {}",
                      instrument);
                }
            );
    Thread.sleep(2000);
    booksDisposable.dispose();
    exchange.disconnect().blockingAwait();
    log.info(threadDump(true, true));
    Thread.sleep(1000);
  }

  private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
    StringBuffer threadDump = new StringBuffer(System.lineSeparator());
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
      if (threadInfo.getThreadName().contains("snapshots") || threadInfo.getThreadName()
          .contains("RxCached")) {
        threadDump.append(threadInfo);
      }
    }
    return threadDump.toString();
  }

}
