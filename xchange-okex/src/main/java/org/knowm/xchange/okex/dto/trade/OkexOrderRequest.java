package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/* Author: Max Gao (gaamox@tutanota.com) Created: 09-06-2021 */
/** <a href="https://www.okex.com/docs-v5/en/#rest-api-trade-place-order">...</a> * */
@Builder
public class OkexOrderRequest {
  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("tdMode")
  private String tradeMode;

  @JsonProperty("ccy")
  private String marginCurrency;

  @JsonProperty("clOrderId")
  private String clientOrderId;

  @JsonProperty("tag")
  private String tag;

  @JsonProperty("side")
  private String side;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("ordType")
  private String orderType;

  @JsonProperty("sz")
  private String amount;

  @JsonProperty("px")
  private String price;

  @JsonProperty("reduceOnly")
  private boolean reducePosition;

  /*  Whether to disallow the system from amending the size of the SPOT Market Order.
  Valid options: true or false. The default value is false.
  If true, system will not amend and reject the market order if user does not have sufficient funds.
  Only applicable to SPOT Market Orders */
  @JsonProperty("banAmend")
  private boolean banAmend;

  /*  Take-profit trigger price
  If you fill in this parameter, you should fill in the take-profit order price as well.*/
  @JsonProperty("tpTriggerPx")
  private String tpTriggerPx;

/*  Take-profit order price
  If you fill in this parameter, you should fill in the take-profit trigger price as well.
  If the price is -1, take-profit will be executed at the market price.*/
  @JsonProperty("tpOrdPx")
  private String tpOrdPx;

/*  Stop-loss trigger price
  If you fill in this parameter, you should fill in the stop-loss order price.*/
  @JsonProperty("slTriggerPx")
  private String slTriggerPx;

/*  Stop-loss order price
  If you fill in this parameter, you should fill in the stop-loss trigger price.
  If the price is -1, stop-loss will be executed at the market price.*/
  @JsonProperty("slOrdPx")
  private String slOrdPx;

/*  Take-profit trigger price type
  last: last price
  index: index price
  mark: mark price
  The Default is last*/
  @JsonProperty("tpTriggerPxType")
  private String tpTriggerPxType;

  /*  Stop-loss trigger price type
  last: last price
  index: index price
  mark: mark price
  The Default is last*/
  @JsonProperty("slTriggerPxType")
  private String slTriggerPxType;
}
