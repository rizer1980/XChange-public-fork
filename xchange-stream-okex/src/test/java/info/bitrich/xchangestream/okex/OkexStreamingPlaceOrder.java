package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.disposables.Disposable;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.knowm.xchange.okex.OkexExchange.PARAM_USE_AWS;

public class OkexStreamingPlaceOrder {
  StreamingExchange exchange;
  private final Instrument instrument = new FuturesContract("XMR/USDT/SWAP");

  @Before
  public void setUp() {
    Properties properties = new Properties();
    try {
      properties.load(this.getClass().getResourceAsStream("/secret.keys"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // Enter your authentication details here to run private endpoint tests
    final String API_KEY =
        (properties.getProperty("apikey") == null)
            ? System.getenv("okx_apikey")
            : properties.getProperty("apikey");
    final String SECRET_KEY =
        (properties.getProperty("secret") == null)
            ? System.getenv("okx_secretkey")
            : properties.getProperty("secret");
    final String PASSPHRASE =
        (properties.getProperty("passphrase") == null)
            ? System.getenv("okx_passphrase")
            : properties.getProperty("passphrase");

    ExchangeSpecification spec = new OkexStreamingExchange().getDefaultExchangeSpecification();
    spec.setApiKey(API_KEY);
    spec.setSecretKey(SECRET_KEY);
    spec.setExchangeSpecificParametersItem(OkexExchange.PARAM_PASSPHRASE, PASSPHRASE);
    spec.setExchangeSpecificParametersItem(OkexExchange.USE_SANDBOX, true);
    spec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, "1");

    if (System.getenv("proxy_host") != null) spec.setProxyHost(System.getenv("proxy_host"));
    if (System.getenv("proxy_port") != null)
      spec.setProxyPort(Integer.valueOf(System.getenv("proxy_port")));

    spec.setExchangeSpecificParametersItem(PARAM_USE_AWS, "false");
    exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec);
    exchange.connect().blockingAwait();
  }

  @Test
  public void checkPlaceOrderSocket() throws IOException {
    OkexStreamingExchange okexStreamingExchange = (OkexStreamingExchange) exchange;
    OrderBook ob = exchange.getMarketDataService().getOrderBook(instrument);
    OkexStreamingTradeService okexStreamingTradeService =
        (OkexStreamingTradeService) okexStreamingExchange.getStreamingTradeService();
    LimitOrder lo =
        new LimitOrder.Builder(Order.OrderType.BID, instrument)
            .limitPrice(ob.getAsks().get(0).getLimitPrice())
            .originalAmount(
                exchange.getExchangeMetaData().getInstruments().get(instrument).getMinimumAmount())
            .timestamp(new Date())
            .userReference("test12345")
            .build();
    okexStreamingTradeService.placeLimitOrder(lo);
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
