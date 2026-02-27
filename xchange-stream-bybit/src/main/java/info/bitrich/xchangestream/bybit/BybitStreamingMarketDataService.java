package info.bitrich.xchangestream.bybit;

import static info.bitrich.xchangestream.bybit.BybitStreamAdapters.adaptFundingRateInterval;
import static org.knowm.xchange.bybit.BybitAdapters.convertToBybitSymbol;
import static org.knowm.xchange.dto.Order.OrderType.ASK;
import static org.knowm.xchange.dto.Order.OrderType.BID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import dto.BybitResponse;
import dto.marketdata.BybitOrderbook;
import dto.marketdata.BybitPublicOrder;
import dto.trade.BybitTrade;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.knowm.xchange.bybit.dto.marketdata.tickers.linear.BybitLinearInverseTicker;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BybitStreamingMarketDataService implements StreamingMarketDataService {

  private final Logger LOG = LoggerFactory.getLogger(BybitStreamingMarketDataService.class);
  private final BybitStreamingService streamingService;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  public static final String TRADE = "publicTrade.";
  public static final String ORDERBOOK = "orderbook.";
  public static final String TICKER = "tickers.";

  private final Map<String, OrderBook> orderBookMap = new HashMap<>();
  private final Map<Instrument, PublishSubject<List<OrderBookUpdate>>>
      orderBookUpdatesSubscriptions;

  private final Map<String, FundingRate> fundingRateMap = new HashMap<>();

  public BybitStreamingMarketDataService(BybitStreamingService streamingService) {
    this.streamingService = streamingService;
    this.orderBookUpdatesSubscriptions = new ConcurrentHashMap<>();
  }

  /**
   * Linear & inverse: Level 1 data, push frequency: 10ms Level 50 data, push frequency: 20ms Level 200 data, push frequency: 100ms Level 500 data, push frequency: 100ms Spot: Level 1 data, push
   * frequency: 10ms Level 50 data, push frequency: 20ms Level 200 data, push frequency: 200ms
   *
   * @param args - orderbook depth or several depths
   */
  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    List<Integer> depths;
    List<AtomicLong> orderBookUpdateIdPrev = new ArrayList<>();
    if (args.length > 0 && args[0] != null) {
      depths = Arrays.stream(args[0].toString().split(","))
          .map(String::trim) // Optional: remove leading/trailing whitespace
          .map(Integer::parseInt)
          .collect(Collectors.toList());
      // highest first, to receive snapshot
      depths.sort(Comparator.reverseOrder());
    } else {
      depths = new ArrayList<>();
      depths.add(50);
    }
    String orderBookMapId = ORDERBOOK + convertToBybitSymbol(instrument);
    List<Observable<OrderBook>> observableList = new ArrayList<>();
    for (int i = 0; i < depths.size(); i++) {
      orderBookUpdateIdPrev.add(new AtomicLong());
      String channelUniqueId = ORDERBOOK + depths.get(i) + "." + convertToBybitSymbol(instrument);
      int finalI = i;
      observableList.add(streamingService
          .subscribeChannel(channelUniqueId)
          .map(
              jsonNode -> {
                try {
                  BybitOrderbook bybitOrderBooks = mapper.treeToValue(jsonNode, BybitOrderbook.class);
                  String type = bybitOrderBooks.getDataType();
                  if (type.equalsIgnoreCase("snapshot")) {
                    orderBookUpdateIdPrev.get(finalI).set(bybitOrderBooks.getData().getU());
                    if (depths.get(finalI) == 1) {
                      // If we are processing a level 1 snapshot, we procees it like a ticker, remove higher(BID) or lower(ASK) levels
                      OrderBook orderBook = orderBookMap.getOrDefault(orderBookMapId, null);
                      if (orderBook == null) {
                        LOG.error("Failed to get orderBook, orderBookMapId= {}", orderBookMapId);
                        return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList(), false);
                      }
                      updateAsTicker(orderBook.getBids(), orderBook, bybitOrderBooks.getData().getBid().get(0), BID, instrument, new Date(bybitOrderBooks.getCts()));
                      updateAsTicker(orderBook.getAsks(), orderBook, bybitOrderBooks.getData().getAsk().get(0), ASK, instrument, new Date(bybitOrderBooks.getCts()));
                      return orderBook;
                    }
                    // snapshot only for first stream
                    if (finalI == 0) {
                      OrderBook orderBook =
                          BybitStreamAdapters.adaptOrderBook(bybitOrderBooks, instrument);
                      orderBookMap.put(orderBookMapId, orderBook);
                      return orderBook;
                    }
                  } else if (type.equalsIgnoreCase("delta")) {
                      return applyOrderBookDeltaSnapshot(
                          orderBookMapId, instrument, bybitOrderBooks, orderBookUpdateIdPrev.get(finalI));
                  }
                  return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList(), false);
                } catch (IllegalStateException e) {
                  LOG.warn(
                      "Resubscribing {} channel after adapter error {}", instrument, e.getMessage());
                  // Resubscribe to the channel, triggering a new snapshot
                  orderBookMap.remove(orderBookMapId);
                  if (streamingService.isSocketOpen()) {
                    streamingService.sendMessage(
                        streamingService.getUnsubscribeMessage(channelUniqueId, args));
                    streamingService.sendMessage(
                        streamingService.getSubscribeMessage(channelUniqueId, args));
                  }
                  return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList(), false);
                }
              })
          .filter(orderBook -> !orderBook.getBids().isEmpty() && !orderBook.getAsks().isEmpty()));

    }
    return Observable.merge(observableList);
  }

  private OrderBook applyOrderBookDeltaSnapshot(
      String orderBookMapId,
      Instrument instrument,
      BybitOrderbook bybitOrderBookUpdate,
      AtomicLong orderBookUpdateIdPrev) {
    OrderBook orderBook = orderBookMap.getOrDefault(orderBookMapId, null);
    if (orderBook == null) {
      LOG.error("Failed to get orderBook, orderBookMapId= {}", orderBookMapId);
      return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList(), false);
    }
    if (orderBookUpdateIdPrev.incrementAndGet() == bybitOrderBookUpdate.getData().getU()) {
      LOG.debug(
          "orderBookUpdate id {}, seq {} ",
          bybitOrderBookUpdate.getData().getU(),
          bybitOrderBookUpdate.getData().getSeq());
      List<BybitPublicOrder> asks = bybitOrderBookUpdate.getData().getAsk();
      List<BybitPublicOrder> bids = bybitOrderBookUpdate.getData().getBid();
      Date timestamp = new Date(bybitOrderBookUpdate.getCts());
      asks.forEach(
          bybitPublicOrder ->
              orderBook.update(
                  BybitStreamAdapters.adaptOrderBookOrder(
                      bybitPublicOrder, instrument, Order.OrderType.ASK, timestamp)));
      bids.forEach(
          bybitPublicOrder ->
              orderBook.update(
                  BybitStreamAdapters.adaptOrderBookOrder(
                      bybitPublicOrder, instrument, Order.OrderType.BID, timestamp)));
      if (orderBookUpdatesSubscriptions.get(instrument) != null) {
        orderBookUpdatesSubscriptions(instrument, asks, bids, timestamp);
      }
      return orderBook;
    } else {
      LOG.error(
          "orderBookUpdate id sequence failed, expected {}, in fact {}",
          orderBookUpdateIdPrev.get(),
          bybitOrderBookUpdate.getData().getU());
      throw new IllegalStateException("orderBookUpdate id sequence failed");
    }
  }

  private void updateAsTicker(List<LimitOrder> limitOrders, OrderBook orderBook, BybitPublicOrder order, Order.OrderType orderType, Instrument instrument, Date timeStamp) {
    BigDecimal amount = new BigDecimal(order.getSize());
    BigDecimal price = new BigDecimal(order.getPrice());
    LimitOrder lo = BybitStreamAdapters.adaptOrderBookOrder(order, instrument, orderType, timeStamp);
    long stamp = orderBook.lock.readLock();
    int idx = Collections.binarySearch(limitOrders, lo);
    try {
      while (true) {
        long writeStamp = orderBook.lock.tryConvertToWriteLock(stamp);
        if (writeStamp != 0L) {
          stamp = writeStamp;

          if (idx == 0) {
            if (limitOrders.get(0).getOriginalAmount().compareTo(amount) != 0) {
              limitOrders.remove(0);
              limitOrders.add(0, lo);
              LOG.debug("updateAsTicker+, removed first: {} {} {} {} {} {}", instrument, orderType, timeStamp, amount, price, limitOrders.size());
              orderBook.updateDate(timeStamp);
            }
            break; // fully equal, skip
          }
          if (idx >= 1) {
            limitOrders.subList(0, idx + 1).clear();
            limitOrders.add(0, lo);
            LOG.debug("updateAsTicker+, removed idx: {} {} {} {} {} {} {}", idx, instrument, orderType, timeStamp, amount, price, limitOrders.size());
            orderBook.updateDate(timeStamp);
            break;
          }
          // idx <0
          idx = -idx - 1;
          if (idx == 0) { //  higher than first
            limitOrders.add(0, lo);
            LOG.debug("updateAsTicker-, added to first place: {} {} {} {} {} {}", instrument, orderType, timeStamp, amount, price, limitOrders.size());
            orderBook.updateDate(timeStamp);
          } else {
            limitOrders.subList(0, idx).clear();
            limitOrders.add(0, lo);
            LOG.debug("updateAsTicker-, removed idx: {} {} {} {} {} {} {}", idx - 1, instrument, orderType, timeStamp, amount, price, limitOrders.size());
            orderBook.updateDate(timeStamp);
          }
          break;
        } else {
          orderBook.lock.unlockRead(stamp);
          stamp = orderBook.lock.writeLock();
          // here wee need to recheck idx, because it is possible that orderBook changed between
          // unlockRead and writeLock
          if (recheckIdx(limitOrders, price, idx)) {
            idx = Collections.binarySearch(limitOrders, lo);
          }
        }
      }
    } finally {
      orderBook.lock.unlock(stamp);
    }
  }

  private boolean recheckIdx(List<LimitOrder> limitOrders, BigDecimal price, int idx) {
    switch (idx) {
      case 0: {
        if (!limitOrders.isEmpty()) {
          // if not equals, need to recheck
          return limitOrders.get(0).getLimitPrice().compareTo(price) != 0;
        } else {
          return true;
        }
      }
      case -1: {
        if (limitOrders.isEmpty()) {
          return false;
        } else {
          return limitOrders.get(0).getLimitPrice().compareTo(price) <= 0;
        }
      }
      default:
        return true;
    }
  }

  public static int indexedBinarySearch(List<LimitOrder> list, BigDecimal price, boolean reverseOrder) {
    int low = 0;
    int high = list.size() - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      BigDecimal midVal = list.get(mid).getLimitPrice();
      int cmp;
      if (!reverseOrder) {
        cmp = midVal.compareTo(price);
      } else {
        cmp = -midVal.compareTo(price);
      }
      if (cmp < 0) {
        low = mid + 1;
      } else if (cmp > 0) {
        high = mid - 1;
      } else {
        return mid; // key found
      }
    }
    return -(low + 1);  // key not found
  }

  @Override
  public Observable<List<OrderBookUpdate>> getOrderBookUpdates(
      Instrument instrument, Object... args) {
    return orderBookUpdatesSubscriptions.computeIfAbsent(instrument, v -> PublishSubject.create());
  }

  private void orderBookUpdatesSubscriptions(
      Instrument instrument, List<BybitPublicOrder> asks, List<BybitPublicOrder> bids, Date date) {
    List<OrderBookUpdate> orderBookUpdates = new ArrayList<>();
    for (BybitPublicOrder ask : asks) {
      OrderBookUpdate o =
          new OrderBookUpdate(
              Order.OrderType.ASK,
              new BigDecimal(ask.getSize()),
              instrument,
              new BigDecimal(ask.getPrice()),
              date,
              new BigDecimal(ask.getSize()));
      orderBookUpdates.add(o);
    }
    for (BybitPublicOrder bid : bids) {
      OrderBookUpdate o =
          new OrderBookUpdate(
              Order.OrderType.BID,
              new BigDecimal(bid.getSize()),
              instrument,
              new BigDecimal(bid.getPrice()),
              date,
              new BigDecimal(bid.getSize()));
      orderBookUpdates.add(o);
    }
    orderBookUpdatesSubscriptions.get(instrument).onNext(orderBookUpdates);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    String channelUniqueId = TRADE + convertToBybitSymbol(instrument);

    return streamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<BybitTrade> bybitTradeList =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, BybitTrade.class));
              return Observable.fromIterable(
                  BybitStreamAdapters.adaptTrades(bybitTradeList, instrument).getTrades());
            });
  }

  @Override
  public Observable<FundingRate> getFundingRate(Instrument instrument, Object... args) {
    String channelUniqueId = TICKER + convertToBybitSymbol(instrument);
    return streamingService
        .subscribeChannel(channelUniqueId)
        .map(
            jsonNode -> {
              BybitResponse<BybitLinearInverseTicker> bybitTicker =
                  mapper.treeToValue(jsonNode, new TypeReference<>() {
                  });
              String type = bybitTicker.getType();
              if (type.equalsIgnoreCase("snapshot")) {
                FundingRate fundingRate =
                    BybitStreamAdapters.adaptFundingRate(bybitTicker.getData());
                fundingRateMap.put(channelUniqueId, fundingRate);
                return fundingRate;
              } else if (type.equalsIgnoreCase("delta")) {
                return applyFundingRateDelta(channelUniqueId, bybitTicker.getData());
              }
              return new FundingRate();
            })
        .filter(pred -> pred.getFundingRate() != null);
  }

  private FundingRate applyFundingRateDelta(
      String channelUniqueId, BybitLinearInverseTicker bybitTicker) {
    FundingRate fundingRate = fundingRateMap.getOrDefault(channelUniqueId, null);
    if (fundingRate != null) {
      boolean returnNew = false;
      if (bybitTicker.getFundingRate() != null) {
        fundingRate.setFundingRate(bybitTicker.getFundingRate());
        returnNew = true;
      }
      if (bybitTicker.getNextFundingTime() != null) {
        fundingRate.setFundingRateDate(bybitTicker.getNextFundingTime());
        returnNew = true;
      }
      if (bybitTicker.getFundingIntervalHour() != null) {
        fundingRate.setFundingRateInterval(
            adaptFundingRateInterval(bybitTicker.getFundingIntervalHour()));
        returnNew = true;
      }
      if (returnNew) {
        fundingRate.setFundingRate1h(
            fundingRate
                .getFundingRate()
                .divide(
                    BigDecimal.valueOf(fundingRate.getFundingRateInterval().getHours()),
                    fundingRate.getFundingRate().scale(),
                    RoundingMode.HALF_EVEN));
        fundingRate.setFundingRateEffectiveInMinutes(
            TimeUnit.MILLISECONDS.toMinutes(
                fundingRate.getFundingRateDate().getTime() - System.currentTimeMillis()));
        return fundingRate;
      } else {
        return new FundingRate();
      }
    } else {
      LOG.error("Failed to get fundingRate, channelUniqueId= {}", channelUniqueId);
      return new FundingRate();
    }
  }
}
