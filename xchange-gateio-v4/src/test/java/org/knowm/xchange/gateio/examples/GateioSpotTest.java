package org.knowm.xchange.gateio.examples;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.utils.AuthUtils;

import java.io.IOException;
import java.util.Date;

public class GateioSpotTest {
  private static final Instrument currencyPair = CurrencyPair.BTC_USDT;
  public static Exchange exchange;

  public static void main(String[] args) throws IOException, InterruptedException {
    init();
    Thread.sleep(1000);
    candleStick();
  }

  private static void candleStick() throws IOException {
    CandleStickDataParams params = new DefaultCandleStickParam(new Date(System.currentTimeMillis() - 86400000 * 4), new Date(), 86400);
    exchange.getMarketDataService().getCandleStickData(currencyPair, params).getCandleSticks().forEach(System.out::println);
  }

  private static void init() {
    ExchangeSpecification exchangeSpecification =
        new ExchangeSpecification(GateioExchange.class);
    AuthUtils.setApiAndSecretKey(exchangeSpecification, "gateio-main");
    exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
  }
}
