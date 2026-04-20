package org.knowm.xchange.gateio.examples;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CandleStickDataParams;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import org.knowm.xchange.utils.AuthUtils;

import java.io.IOException;
import java.util.Date;

import static org.knowm.xchange.gateio.GateioExchange.EXCHANGE_TYPE;
import static org.knowm.xchange.gateio.dto.GateioExchangeType.FUTURES;

public class GateioFuturesTest {
  private static final Instrument instrument = new FuturesContract("ETH/USDT/PERP");
  public static Exchange exchange;

  @Before
  public void before() throws IOException {
    init();
  }

  @Test
  @Ignore
  public void candleStick() throws IOException {
    CandleStickDataParams params = new DefaultCandleStickParamWithLimit(new Date(System.currentTimeMillis() - 86400000 * 4), new Date(), 86400, 2);
    exchange.getMarketDataService().getCandleStickData(instrument, params).getCandleSticks().forEach(System.out::println);
  }

  @Test
  public void setLeverage() throws IOException {
    exchange.getAccountService().setLeverage(instrument, 1);
  }

  private void init() {
    ExchangeSpecification exchangeSpecification =
        new ExchangeSpecification(GateioExchange.class);
    exchangeSpecification.setExchangeSpecificParametersItem(EXCHANGE_TYPE, FUTURES);
    AuthUtils.setApiAndSecretKey(exchangeSpecification, "gateio-main");
    exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
  }
}
