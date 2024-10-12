package org.knowm.xchange.bybit;

import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE;

import java.io.IOException;
import java.math.BigDecimal;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;

public class TradeExampleDemoAccount {

  public static void main(String[] args) {
    try {
      testTradeDemoAccount();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void testTradeDemoAccount() throws IOException {
    ExchangeSpecification exchangeSpecification =
        new BybitExchange().getDefaultExchangeSpecification();
    exchangeSpecification.setApiKey(System.getProperty("test_api_key"));
    exchangeSpecification.setSecretKey(System.getProperty("test_secret_key"));
    exchangeSpecification.setExchangeSpecificParametersItem(
        SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.UNIFIED);
    exchangeSpecification.setExchangeSpecificParametersItem(
        USE_SANDBOX, true);
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(
        exchangeSpecification);
    Instrument ETH_USDT = new CurrencyPair("ETH/USDT");

    MarketOrder marketOrderSpot = new MarketOrder(OrderType.BID, new BigDecimal("5"), ETH_USDT);
    String marketSpotOrderId = exchange.getTradeService().placeMarketOrder(marketOrderSpot);
    System.out.println("Market Spot order id: " + marketSpotOrderId);
  }
}
