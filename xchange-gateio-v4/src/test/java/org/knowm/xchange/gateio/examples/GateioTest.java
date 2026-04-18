package org.knowm.xchange.gateio.examples;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import org.knowm.xchange.utils.AuthUtils;

import java.io.IOException;
import java.util.Date;

public class GateioTest {
  private static final Instrument currencyPair = CurrencyPair.BTC_USDT;
  private static final Instrument instrument = new FuturesContract("SOL/USDT/PERP");
  public static Exchange exchange;

  public static void main(String[] args) throws IOException {
    init();
    candleStick();
  }

  private static void candleStick() throws IOException {
    CandleStickDataParams params = new DefaultCandleStickParamWithLimit(new Date(System.currentTimeMillis() - 86400000 * 4), new Date(), 86400, 2);
    exchange.getMarketDataService().getCandleStickData(instrument, params).getCandleSticks().forEach(System.out::println);
    params = new DefaultCandleStickParam(new Date(System.currentTimeMillis() - 86400000 * 4), new Date(), 86400);
    exchange.getMarketDataService().getCandleStickData(currencyPair, params).getCandleSticks().forEach(System.out::println);
  }

  private static void init() {
    ExchangeSpecification exchangeSpecification =
        new ExchangeSpecification(GateioExchange.class);
    AuthUtils.setApiAndSecretKey(exchangeSpecification, "gateio-main");
    exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
  }
}
