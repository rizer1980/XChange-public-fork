package org.knowm.xchange.binance.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.instrument.Instrument;

@Getter
@ToString
public class BinanceFundingRateHistory {

  private final Instrument instrument;
  private final BigDecimal fundingRate;
  private final Date fundingTime;
  private final BigDecimal markPrice;

  public BinanceFundingRateHistory(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("fundingRate") BigDecimal fundingRate,
      @JsonProperty("fundingTime") long fundingTime,
      @JsonProperty("markPrice") BigDecimal markPrice) {
    this.instrument = BinanceAdapters.adaptSymbol(symbol, true);
    this.fundingRate = fundingRate;
    this.fundingTime = new Date(fundingTime);
    this.markPrice = markPrice;
  }
}
