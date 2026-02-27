package info.bitrich.xchangestream.bybit.example;

import static info.bitrich.xchangestream.bybit.example.BaseBybitExchange.connectDemoApi;
import static info.bitrich.xchangestream.bybit.example.BaseBybitExchange.connectMainApi;

import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.sql.SQLOutput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BybitStreamOrderBookAndFeesExample {

  private static final Logger LOG =
      LoggerFactory.getLogger(BybitStreamOrderBookAndFeesExample.class);
  static Instrument instrument = new FuturesContract("POWER/USDT/PERP");

  public static void main(String[] args) {
    try {
      // Stream orderBook and OrderBookUpdates
      getOrderBookExample();
      getFeesExample();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    exchange.disconnect().blockingAwait();
  }

  public static void overlapCheck(Instant now, OrderBook orderBook, Instrument instrument, String from) {
    if (orderBook.getAsks().get(0).getLimitPrice().doubleValue() - orderBook.getBids().get(0).getLimitPrice().doubleValue() < 0) {
      LOG.warn("{},timestamp {}, OrderBook overlap warn,  instrument {}", from, now, instrument);
      StringBuilder sbAsks = new StringBuilder();
      int count = 0;
      for (LimitOrder order : orderBook.getAsks()) {
        sbAsks.append(count).append(": ");
        sbAsks.append(order.getLimitPrice()).append("/").append(order.getOriginalAmount()).append(" ");
        if (count++ > 2) {
          break;
        }
      }
      StringBuilder sbBids = new StringBuilder();
      count = 0;
      for (LimitOrder order : orderBook.getBids()) {
        sbBids.append(count).append(":");
        sbBids.append(order.getLimitPrice()).append("/").append(order.getOriginalAmount()).append(" ");
        if (count++ > 2) {
          break;
        }
      }
      LOG.debug("ask {}, bid {}", sbAsks.toString(), sbBids.toString());
    }
  }
  static List<Disposable> booksDisposable = new ArrayList<>();
  static StreamingExchange exchange;

  private static void getFeesExample() {
    exchange = connectMainApi(BybitCategory.LINEAR, true);
    // if auth response is not received at this moment, wee get non-auth exception here
    // var fees =
    // exchange.getAccountService().getDynamicTradingFeesByInstrument(BybitCategory.LINEAR);
    // OPTION - wait for login message response
    while (!exchange.isAlive()) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Map<Instrument, Fee> fees;
    try {
      fees =
          exchange
              .getAccountService()
              .getDynamicTradingFeesByInstrument(BybitCategory.LINEAR.getValue());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LOG.info("fees: {}", fees);
  }

  private static void getOrderBookExample() throws InterruptedException {
    exchange = connectMainApi(BybitCategory.LINEAR, false);
    subscribeOrderBook("200,50");
    subscribeOrderBook("1");
    Thread.sleep(120000L);
    for (Disposable dis : booksDisposable) {
      dis.dispose();
    }
    exchange.disconnect().blockingAwait();
  }

  private static void subscribeOrderBook(String depth) {
    //                  overlapCheck(Instant.now(), orderBook, instrument, "subscribeOrderBook");
    booksDisposable.add(
        exchange
            .getStreamingMarketDataService()
            .getOrderBook(instrument, depth)
            .doOnError(
                error -> {
                  LOG.error(error.getMessage());
                  for (Disposable dis : booksDisposable) {
                    dis.dispose();
                  }
                })
            .subscribe(
                orderbook -> System.out.print("."),
                throwable -> {
                  LOG.error(throwable.getMessage());
                }));
  }
}
