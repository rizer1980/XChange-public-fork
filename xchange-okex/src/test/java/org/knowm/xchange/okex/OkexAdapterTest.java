package org.knowm.xchange.okex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.knowm.xchange.okex.dto.OkexInstType.SPOT;
import static org.knowm.xchange.okex.dto.OkexInstType.SWAP;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.OrderHedgeMode;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexSetPositionModeRequest;
import org.knowm.xchange.okex.dto.account.OkexSetPositionModeResponse;
import org.knowm.xchange.okex.dto.account.OkexTradeFee;
import org.knowm.xchange.okex.dto.trade.OkexAmendOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;
import org.knowm.xchange.okex.dto.trade.OkexOrderFlags;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;

class OkexAdapterTest {

  private ObjectMapper mapper;
  private ExchangeMetaData exchangeMetaData;
  private CurrencyPair btcUsdtPair;
  private CurrencyPair btcUsdcPair;
  private CurrencyPair ethUsdtPair;
  private FuturesContract btcUsdtSwap;
  private FuturesContract ethUsdtSwap;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    btcUsdtPair = new CurrencyPair("BTC", "USDT");
    btcUsdcPair = new CurrencyPair("BTC", "USDC");
    ethUsdtPair = new CurrencyPair("ETH", "USDT");
    btcUsdtSwap = new FuturesContract("BTC/USDT/SWAP");
    ethUsdtSwap = new FuturesContract("ETH/USDT/SWAP");

    Map<Instrument, InstrumentMetaData> instruments = new HashMap<>();
    instruments.put(
        btcUsdtPair, InstrumentMetaData.builder().contractValue(BigDecimal.ONE).build());
    instruments.put(
        btcUsdcPair, InstrumentMetaData.builder().contractValue(BigDecimal.ONE).build());
    instruments.put(
        ethUsdtPair, InstrumentMetaData.builder().contractValue(BigDecimal.ONE).build());
    instruments.put(
        btcUsdtSwap, InstrumentMetaData.builder().contractValue(new BigDecimal("0.01")).build());
    instruments.put(
        ethUsdtSwap, InstrumentMetaData.builder().contractValue(new BigDecimal("0.1")).build());

    exchangeMetaData = new ExchangeMetaData(instruments, new HashMap<>(), null, null, true);

    OkexAdapters.instrumentToInstrumentIdMap.put(btcUsdtPair, 1001L);
    OkexAdapters.instrumentToInstrumentIdMap.put(btcUsdcPair, 1002L);
    OkexAdapters.instrumentToInstrumentIdMap.put(ethUsdtPair, 1003L);
    OkexAdapters.instrumentToInstrumentIdMap.put(btcUsdtSwap, 2001L);
    OkexAdapters.instrumentToInstrumentIdMap.put(ethUsdtSwap, 2002L);
  }

  @Test
  void adaptTradingFee() throws Exception {
    InputStream is = OkexAdapterTest.class.getResourceAsStream("/getFeeRatesSpot.json5");
    OkexTradeFee okexSpotTradeFee =
        mapper
            .readValue(is, new TypeReference<OkexResponse<List<OkexTradeFee>>>() {})
            .getData()
            .get(0);
    assertThat(OkexAdapters.adaptTradingFee(okexSpotTradeFee, SPOT, new CurrencyPair("USDT/SGD")))
        .isEqualTo(new Fee(new BigDecimal("0.0005"), new BigDecimal("0.0007")));
    assertThat(OkexAdapters.adaptTradingFee(okexSpotTradeFee, SPOT, new CurrencyPair("OKB/BTC")))
        .isEqualTo(new Fee(new BigDecimal("0.0005"), new BigDecimal("0.0007")));
    assertThat(OkexAdapters.adaptTradingFee(okexSpotTradeFee, SPOT, new CurrencyPair("USDC/USDT")))
        .isEqualTo(new Fee(new BigDecimal("0.0008"), new BigDecimal("0.001")));
    assertThat(OkexAdapters.adaptTradingFee(okexSpotTradeFee, SPOT, new CurrencyPair("EUR/USDT")))
        .isEqualTo(new Fee(new BigDecimal("0.0008"), new BigDecimal("0.001")));

    is = OkexAdapterTest.class.getResourceAsStream("/getFeeRatesSwap.json5");
    OkexTradeFee okexSwapTradeFee =
        mapper
            .readValue(is, new TypeReference<OkexResponse<List<OkexTradeFee>>>() {})
            .getData()
            .get(0);
    assertThat(
            OkexAdapters.adaptTradingFee(
                okexSwapTradeFee, SWAP, new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkexAdapters.adaptTradingFee(
                okexSwapTradeFee, SWAP, new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkexAdapters.adaptTradingFee(
                okexSwapTradeFee, SWAP, new FuturesContract("USDC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
  }

  @Test
  void testSetPositionModeSerializationAndDeserialization() throws Exception {
    OkexSetPositionModeRequest request =
        OkexSetPositionModeRequest.builder().posMode("long_short_mode").build();
    String jsonRequest = mapper.writeValueAsString(request);
    assertThat(jsonRequest).contains("\"posMode\":\"long_short_mode\"");

    String jsonResponse =
        "{\"code\":\"0\",\"msg\":\"\",\"data\":[{\"posMode\":\"long_short_mode\"}]}";
    OkexResponse<List<OkexSetPositionModeResponse>> response =
        mapper.readValue(
            jsonResponse, new TypeReference<OkexResponse<List<OkexSetPositionModeResponse>>>() {});
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).hasSize(1);
    assertThat(response.getData().get(0).getPosMode()).isEqualTo("long_short_mode");
  }

  @Test
  void testAdaptOrderFromOkexOrderDetailsSpotLive() throws Exception {
    String jsonOrder =
        "{\n"
            + "  \"instType\": \"SPOT\",\n"
            + "  \"instId\": \"BTC-USDT\",\n"
            + "  \"ordId\": \"312269865356374016\",\n"
            + "  \"clOrdId\": \"client-order-1\",\n"
            + "  \"side\": \"buy\",\n"
            + "  \"posSide\": \"net\",\n"
            + "  \"ordType\": \"limit\",\n"
            + "  \"sz\": \"2.5\",\n"
            + "  \"px\": \"50000.5\",\n"
            + "  \"accFillSz\": \"0.5\",\n"
            + "  \"avgPx\": \"49900.25\",\n"
            + "  \"state\": \"live\",\n"
            + "  \"fee\": \"-0.001\",\n"
            + "  \"feeCcy\": \"USDT\",\n"
            + "  \"cTime\": \"1623321600000\",\n"
            + "  \"uTime\": \"1623321650000\"\n"
            + "}";

    OkexOrderDetails details = mapper.readValue(jsonOrder, OkexOrderDetails.class);
    LimitOrder adapted = OkexAdapters.adaptOrder(details, exchangeMetaData);

    assertThat(adapted.getType()).isEqualTo(OrderType.BID);
    assertThat(adapted.getInstrument()).isEqualTo(btcUsdtPair);
    assertThat(adapted.getId()).isEqualTo("312269865356374016");
    assertThat(adapted.getUserReference()).isEqualTo("client-order-1");
    assertThat(adapted.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("2.5"));
    assertThat(adapted.getLimitPrice()).isEqualByComparingTo(new BigDecimal("50000.5"));
    assertThat(adapted.getAveragePrice()).isEqualByComparingTo(new BigDecimal("49900.25"));
    assertThat(adapted.getCumulativeAmount()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(adapted.getFee()).isEqualByComparingTo(new BigDecimal("-0.001"));
    assertThat(adapted.getFeeCurrency()).isEqualTo(new Currency("USDT"));
    assertThat(adapted.getStatus()).isEqualTo(OrderStatus.OPEN);
    assertThat(adapted.getTimestamp()).isEqualTo(new Date(1623321600000L));
  }

  @Test
  void testAdaptOrderFromOkexOrderDetailsEmptyAvgPriceAndStatuses() throws Exception {
    String jsonFilled =
        "{\n"
            + "  \"instType\": \"SPOT\",\n"
            + "  \"instId\": \"BTC-USDT\",\n"
            + "  \"ordId\": \"312269865356374017\",\n"
            + "  \"clOrdId\": \"client-order-2\",\n"
            + "  \"side\": \"sell\",\n"
            + "  \"posSide\": \"net\",\n"
            + "  \"ordType\": \"limit\",\n"
            + "  \"sz\": \"1.0\",\n"
            + "  \"px\": \"52000.0\",\n"
            + "  \"accFillSz\": \"1.0\",\n"
            + "  \"avgPx\": \"\",\n"
            + "  \"state\": \"filled\",\n"
            + "  \"fee\": \"-0.002\",\n"
            + "  \"cTime\": \"1623321700000\"\n"
            + "}";

    OkexOrderDetails filledDetails = mapper.readValue(jsonFilled, OkexOrderDetails.class);
    LimitOrder filledOrder = OkexAdapters.adaptOrder(filledDetails, exchangeMetaData);

    assertThat(filledOrder.getType()).isEqualTo(OrderType.ASK);
    assertThat(filledOrder.getAveragePrice()).isEqualTo(BigDecimal.ZERO);
    assertThat(filledOrder.getStatus()).isEqualTo(OrderStatus.FILLED);
    assertThat(filledOrder.getFeeCurrency()).isNull();

    String jsonCanceled = jsonFilled.replace("\"state\": \"filled\"", "\"state\": \"canceled\"");
    OkexOrderDetails canceledDetails = mapper.readValue(jsonCanceled, OkexOrderDetails.class);
    LimitOrder canceledOrder = OkexAdapters.adaptOrder(canceledDetails, exchangeMetaData);
    assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);

    String jsonPartiallyFilled =
        jsonFilled.replace("\"state\": \"filled\"", "\"state\": \"partially_filled\"");
    OkexOrderDetails partiallyFilledDetails =
        mapper.readValue(jsonPartiallyFilled, OkexOrderDetails.class);
    LimitOrder partiallyFilledOrder =
        OkexAdapters.adaptOrder(partiallyFilledDetails, exchangeMetaData);
    assertThat(partiallyFilledOrder.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
  }

  @Test
  void testAdaptOrderFromOkexOrderDetailsFuturesContract() throws Exception {
    String jsonSwap =
        "{\n"
            + "  \"instType\": \"SWAP\",\n"
            + "  \"instId\": \"BTC-USDT-SWAP\",\n"
            + "  \"ordId\": \"312269865356374018\",\n"
            + "  \"clOrdId\": \"swap-order-1\",\n"
            + "  \"side\": \"buy\",\n"
            + "  \"posSide\": \"long\",\n"
            + "  \"ordType\": \"limit\",\n"
            + "  \"sz\": \"100\",\n"
            + "  \"px\": \"50000\",\n"
            + "  \"accFillSz\": \"20\",\n"
            + "  \"avgPx\": \"50000\",\n"
            + "  \"state\": \"live\",\n"
            + "  \"fee\": \"-0.01\",\n"
            + "  \"cTime\": \"1623321800000\"\n"
            + "}";

    OkexOrderDetails swapDetails = mapper.readValue(jsonSwap, OkexOrderDetails.class);
    LimitOrder swapOrder = OkexAdapters.adaptOrder(swapDetails, exchangeMetaData);

    assertThat(swapOrder.getInstrument()).isEqualTo(btcUsdtSwap);
    assertThat(swapOrder.getType()).isEqualTo(OrderType.BID);
    // 100 contracts * 0.01 contractValue = 1.0 volume
    assertThat(swapOrder.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("1"));
    assertThat(swapOrder.getCumulativeAmount()).isEqualByComparingTo(new BigDecimal("20"));
    assertThat(swapOrder.getStatus()).isEqualTo(OrderStatus.OPEN);
  }

  @Test
  void testAdaptOrderMarketOrderSpotCash() {
    MarketOrder marketOrder =
        new MarketOrder.Builder(OrderType.BID, btcUsdtPair)
            .originalAmount(new BigDecimal("1.25"))
            .userReference("market-spot-bid")
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(marketOrder, exchangeMetaData, "1");

    assertThat(request.getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(request.getInstIdCode()).isEqualTo("1001");
    assertThat(request.getTradeMode()).isEqualTo("cash");
    assertThat(request.getSide()).isEqualTo("buy");
    assertThat(request.getPosSide()).isNull();
    assertThat(request.isReducePosition()).isFalse();
    assertThat(request.getClientOrderId()).isEqualTo("market-spot-bid");
    assertThat(request.getOrderType()).isEqualTo("market");
    assertThat(request.getAmount()).isEqualTo("1.25");
    assertThat(request.getTargetCurrency()).isEqualTo("base_ccy");
  }

  @Test
  void testAdaptOrderMarketOrderSpotCross() {
    MarketOrder marketOrder =
        new MarketOrder.Builder(OrderType.ASK, btcUsdtPair)
            .originalAmount(new BigDecimal("2.50"))
            .userReference("market-spot-ask")
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(marketOrder, exchangeMetaData, "3");

    assertThat(request.getTradeMode()).isEqualTo("cross");
    assertThat(request.getSide()).isEqualTo("sell");
  }

  @Test
  void testAdaptOrderMarketOrderUsdcCounterCurrency() {
    MarketOrder marketOrder =
        new MarketOrder.Builder(OrderType.BID, btcUsdcPair)
            .originalAmount(new BigDecimal("0.5"))
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(marketOrder, exchangeMetaData, "1");

    assertThat(request.getInstrumentId()).isEqualTo("BTC-USD");
  }

  @Test
  void testAdaptOrderMarketOrderFuturesTwoWayHedgeMode() {
    MarketOrder longOrder =
        new MarketOrder.Builder(OrderType.BID, btcUsdtSwap)
            .originalAmount(new BigDecimal("0.5"))
            .flags(Set.of(OrderHedgeMode.TWOWAY, OkexOrderFlags.REDUCE_ONLY))
            .userReference("fut-long-1")
            .build();

    OkexOrderRequest longRequest = OkexAdapters.adaptOrder(longOrder, exchangeMetaData, "1");

    assertThat(longRequest.getInstrumentId()).isEqualTo("BTC-USDT-SWAP");
    assertThat(longRequest.getInstIdCode()).isEqualTo("2001");
    assertThat(longRequest.getTradeMode()).isEqualTo("cross");
    assertThat(longRequest.getSide()).isEqualTo("buy");
    assertThat(longRequest.getPosSide()).isEqualTo("long");
    assertThat(longRequest.isReducePosition()).isTrue();
    // 0.5 volume / 0.01 contract value = 50 contracts
    assertThat(longRequest.getAmount()).isEqualTo("50");

    MarketOrder shortOrder =
        new MarketOrder.Builder(OrderType.ASK, btcUsdtSwap)
            .originalAmount(new BigDecimal("0.2"))
            .flags(Set.of(OrderHedgeMode.TWOWAY))
            .build();

    OkexOrderRequest shortRequest = OkexAdapters.adaptOrder(shortOrder, exchangeMetaData, "1");
    assertThat(shortRequest.getSide()).isEqualTo("sell");
    assertThat(shortRequest.getPosSide()).isEqualTo("short");
    assertThat(shortRequest.getAmount()).isEqualTo("20");
  }

  @Test
  void testAdaptOrderMarketOrderExitTypes() {
    MarketOrder exitAsk =
        new MarketOrder.Builder(OrderType.EXIT_ASK, btcUsdtSwap)
            .originalAmount(new BigDecimal("0.1"))
            .flags(new HashSet<>(Collections.singleton(OrderHedgeMode.TWOWAY)))
            .build();

    OkexOrderRequest exitAskRequest = OkexAdapters.adaptOrder(exitAsk, exchangeMetaData, "1");
    assertThat(exitAskRequest.getSide()).isEqualTo("buy");
    assertThat(exitAskRequest.getPosSide()).isEqualTo("short");
    assertThat(exitAskRequest.isReducePosition()).isTrue();

    MarketOrder exitBid =
        new MarketOrder.Builder(OrderType.EXIT_BID, btcUsdtSwap)
            .originalAmount(new BigDecimal("0.1"))
            .flags(new HashSet<>(Collections.singleton(OrderHedgeMode.TWOWAY)))
            .build();

    OkexOrderRequest exitBidRequest = OkexAdapters.adaptOrder(exitBid, exchangeMetaData, "1");
    assertThat(exitBidRequest.getSide()).isEqualTo("sell");
    assertThat(exitBidRequest.getPosSide()).isEqualTo("long");
    assertThat(exitBidRequest.isReducePosition()).isTrue();
  }

  @Test
  void testAdaptOrderLimitOrderSpot() {
    LimitOrder limitOrder =
        new LimitOrder.Builder(OrderType.BID, ethUsdtPair)
            .originalAmount(new BigDecimal("3.5"))
            .limitPrice(new BigDecimal("3200.75"))
            .userReference("limit-eth-1")
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(limitOrder, exchangeMetaData, "1");

    assertThat(request.getInstrumentId()).isEqualTo("ETH-USDT");
    assertThat(request.getInstIdCode()).isEqualTo("1003");
    assertThat(request.getTradeMode()).isEqualTo("cash");
    assertThat(request.getSide()).isEqualTo("buy");
    assertThat(request.getPosSide()).isNull();
    assertThat(request.getClientOrderId()).isEqualTo("limit-eth-1");
    assertThat(request.getOrderType()).isEqualTo("limit");
    assertThat(request.getAmount()).isEqualTo("3.5");
    assertThat(request.getPrice()).isEqualTo("3200.75");
    assertThat(request.isReducePosition()).isFalse();
  }

  @Test
  void testAdaptOrderLimitOrderPostOnlyFlag() {
    LimitOrder postOnlyOrder =
        new LimitOrder.Builder(OrderType.ASK, ethUsdtPair)
            .originalAmount(new BigDecimal("1.0"))
            .limitPrice(new BigDecimal("3500.00"))
            .flags(Set.of(OkexOrderFlags.POST_ONLY))
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(postOnlyOrder, exchangeMetaData, "1");

    assertThat(request.getOrderType()).isEqualTo("post_only");
    assertThat(request.getSide()).isEqualTo("sell");
    assertThat(request.getPrice()).isEqualTo("3500.00");
  }

  @Test
  void testAdaptOrderLimitOrderOptimalLimitIocFlagFutures() {
    LimitOrder futuresIocOrder =
        new LimitOrder.Builder(OrderType.BID, ethUsdtSwap)
            .originalAmount(new BigDecimal("2.0"))
            .limitPrice(new BigDecimal("3000.00"))
            .flags(Set.of(OkexOrderFlags.OPTIMAL_LIMIT_IOC))
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(futuresIocOrder, exchangeMetaData, "1");

    assertThat(request.getOrderType()).isEqualTo("optimal_limit_ioc");
    assertThat(request.getInstrumentId()).isEqualTo("ETH-USDT-SWAP");
    // 2.0 volume / 0.1 contractValue = 20 contracts
    assertThat(request.getAmount()).isEqualTo("20");
  }

  @Test
  void testAdaptOrderLimitOrderOptimalLimitIocFlagSpotIgnored() {
    LimitOrder spotIocOrder =
        new LimitOrder.Builder(OrderType.BID, ethUsdtPair)
            .originalAmount(new BigDecimal("2.0"))
            .limitPrice(new BigDecimal("3000.00"))
            .flags(Set.of(OkexOrderFlags.OPTIMAL_LIMIT_IOC))
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(spotIocOrder, exchangeMetaData, "1");

    // Spot cannot use optimal_limit_ioc, falls back to limit
    assertThat(request.getOrderType()).isEqualTo("limit");
  }

  @Test
  void testAdaptOrderLimitOrderReduceOnlyAndTwoWay() {
    LimitOrder reduceOrder =
        new LimitOrder.Builder(OrderType.ASK, ethUsdtSwap)
            .originalAmount(new BigDecimal("1.0"))
            .limitPrice(new BigDecimal("3100.00"))
            .flags(Set.of(OkexOrderFlags.REDUCE_ONLY, OrderHedgeMode.TWOWAY))
            .build();

    OkexOrderRequest request = OkexAdapters.adaptOrder(reduceOrder, exchangeMetaData, "1");

    assertThat(request.getSide()).isEqualTo("sell");
    assertThat(request.getPosSide()).isEqualTo("short");
    assertThat(request.isReducePosition()).isTrue();
    // 1.0 volume / 0.1 contractValue = 10 contracts
    assertThat(request.getAmount()).isEqualTo("10");
  }

  @Test
  void testAdaptOpenOrders() throws Exception {
    String jsonOrder1 =
        "{\n"
            + "  \"instType\": \"SPOT\",\n"
            + "  \"instId\": \"BTC-USDT\",\n"
            + "  \"ordId\": \"1000001\",\n"
            + "  \"side\": \"buy\",\n"
            + "  \"sz\": \"1.0\",\n"
            + "  \"px\": \"50000\",\n"
            + "  \"accFillSz\": \"0\",\n"
            + "  \"avgPx\": \"\",\n"
            + "  \"state\": \"live\",\n"
            + "  \"fee\": \"0\",\n"
            + "  \"cTime\": \"1623321600000\"\n"
            + "}";

    String jsonOrder2 =
        "{\n"
            + "  \"instType\": \"SPOT\",\n"
            + "  \"instId\": \"ETH-USDT\",\n"
            + "  \"ordId\": \"1000002\",\n"
            + "  \"side\": \"sell\",\n"
            + "  \"sz\": \"5.0\",\n"
            + "  \"px\": \"3000\",\n"
            + "  \"accFillSz\": \"1.0\",\n"
            + "  \"avgPx\": \"3000\",\n"
            + "  \"state\": \"live\",\n"
            + "  \"fee\": \"-0.001\",\n"
            + "  \"feeCcy\": \"USDT\",\n"
            + "  \"cTime\": \"1623321650000\"\n"
            + "}";

    OkexOrderDetails order1 = mapper.readValue(jsonOrder1, OkexOrderDetails.class);
    OkexOrderDetails order2 = mapper.readValue(jsonOrder2, OkexOrderDetails.class);

    OpenOrders openOrders =
        OkexAdapters.adaptOpenOrders(Arrays.asList(order1, order2), exchangeMetaData);

    assertThat(openOrders.getOpenOrders()).hasSize(2);
    assertThat(openOrders.getOpenOrders().get(0).getId()).isEqualTo("1000001");
    assertThat(openOrders.getOpenOrders().get(0).getType()).isEqualTo(OrderType.BID);
    assertThat(openOrders.getOpenOrders().get(0).getFeeCurrency()).isNull();
    assertThat(openOrders.getOpenOrders().get(1).getId()).isEqualTo("1000002");
    assertThat(openOrders.getOpenOrders().get(1).getType()).isEqualTo(OrderType.ASK);
    assertThat(openOrders.getOpenOrders().get(1).getFeeCurrency()).isEqualTo(new Currency("USDT"));
  }

  @Test
  void testAdaptAmendOrder() {
    LimitOrder amendOrder =
        new LimitOrder.Builder(OrderType.BID, ethUsdtSwap)
            .id("ord-amend-1")
            .userReference("cl-amend-1")
            .originalAmount(new BigDecimal("3.0"))
            .limitPrice(new BigDecimal("2950.50"))
            .build();

    OkexAmendOrderRequest request = OkexAdapters.adaptAmendOrder(amendOrder, exchangeMetaData);

    assertThat(request.getInstrumentId()).isEqualTo("ETH-USDT-SWAP");
    assertThat(request.getInstIdCode()).isEqualTo("2002");
    assertThat(request.getOrderId()).isEqualTo("ord-amend-1");
    assertThat(request.getClientOrderId()).isEqualTo("cl-amend-1");
    // 3.0 volume / 0.1 contractValue = 30 contracts
    assertThat(request.getAmendedAmount()).isEqualTo("30");
    assertThat(request.getAmendedPrice()).isEqualTo("2950.50");
  }

  @Test
  void testAdaptOrdersChanges() throws Exception {
    String jsonMarket =
        "{\n"
            + "  \"instType\": \"SPOT\",\n"
            + "  \"instId\": \"BTC-USDT\",\n"
            + "  \"ordId\": \"2000001\",\n"
            + "  \"clOrdId\": \"market-cl-1\",\n"
            + "  \"side\": \"buy\",\n"
            + "  \"ordType\": \"market\",\n"
            + "  \"sz\": \"1.5\",\n"
            + "  \"accFillSz\": \"1.5\",\n"
            + "  \"avgPx\": \"50000\",\n"
            + "  \"state\": \"filled\",\n"
            + "  \"fee\": \"-0.0015\",\n"
            + "  \"feeCcy\": \"USDT\",\n"
            + "  \"uTime\": \"1623321900000\"\n"
            + "}";

    String jsonLimit =
        "{\n"
            + "  \"instType\": \"SWAP\",\n"
            + "  \"instId\": \"BTC-USDT-SWAP\",\n"
            + "  \"ordId\": \"2000002\",\n"
            + "  \"clOrdId\": \"limit-cl-2\",\n"
            + "  \"side\": \"sell\",\n"
            + "  \"ordType\": \"limit\",\n"
            + "  \"sz\": \"50\",\n"
            + "  \"px\": \"51000\",\n"
            + "  \"accFillSz\": \"10\",\n"
            + "  \"fillPx\": \"50950\",\n"
            + "  \"avgPx\": \"50950\",\n"
            + "  \"state\": \"partially_filled\",\n"
            + "  \"fee\": \"-0.005\",\n"
            + "  \"feeCcy\": \"USDT\",\n"
            + "  \"uTime\": \"1623321950000\"\n"
            + "}";

    OkexOrderDetails marketDetails = mapper.readValue(jsonMarket, OkexOrderDetails.class);
    OkexOrderDetails limitDetails = mapper.readValue(jsonLimit, OkexOrderDetails.class);

    List<Order> adapted =
        OkexAdapters.adaptOrdersChanges(
            Arrays.asList(marketDetails, limitDetails), exchangeMetaData);

    assertThat(adapted).hasSize(2);

    Order order1 = adapted.get(0);
    assertThat(order1).isInstanceOf(MarketOrder.class);
    assertThat(order1.getType()).isEqualTo(OrderType.BID);
    assertThat(order1.getInstrument()).isEqualTo(btcUsdtPair);
    assertThat(order1.getId()).isEqualTo("2000001");
    assertThat(order1.getUserReference()).isEqualTo("market-cl-1");
    assertThat(order1.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("1.5"));
    assertThat(order1.getCumulativeAmount()).isEqualByComparingTo(new BigDecimal("1.4985"));
    assertThat(order1.getAveragePrice()).isEqualByComparingTo(new BigDecimal("50000"));
    assertThat(order1.getFee()).isEqualByComparingTo(new BigDecimal("75"));
    assertThat(order1.getFeeCurrency()).isEqualTo(new Currency("USDT"));
    assertThat(order1.getStatus()).isEqualTo(OrderStatus.FILLED);
    assertThat(order1.getTimestamp()).isEqualTo(new Date(1623321900000L));

    Order order2 = adapted.get(1);
    assertThat(order2).isInstanceOf(LimitOrder.class);
    LimitOrder limitOrder2 = (LimitOrder) order2;
    assertThat(limitOrder2.getType()).isEqualTo(OrderType.ASK);
    assertThat(limitOrder2.getInstrument()).isEqualTo(btcUsdtSwap);
    assertThat(limitOrder2.getId()).isEqualTo("2000002");
    assertThat(limitOrder2.getUserReference()).isEqualTo("limit-cl-2");
    // 50 * 0.01 = 0.5 volume
    assertThat(limitOrder2.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("0.5"));
    // 10 * 0.01 = 0.1 volume
    assertThat(limitOrder2.getCumulativeAmount()).isEqualByComparingTo(new BigDecimal("0.1"));
    assertThat(limitOrder2.getLimitPrice()).isEqualByComparingTo(new BigDecimal("50950"));
    assertThat(limitOrder2.getAveragePrice()).isEqualByComparingTo(new BigDecimal("50950"));
    assertThat(limitOrder2.getFee()).isEqualByComparingTo(new BigDecimal("0.005"));
    assertThat(limitOrder2.getFeeCurrency()).isEqualTo(new Currency("USDT"));
    assertThat(limitOrder2.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(limitOrder2.getTimestamp()).isEqualTo(new Date(1623321950000L));
  }
}
