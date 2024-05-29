package org.knowm.xchange.bybit.dto.trade;

import org.knowm.xchange.bybit.Bybit;
import org.knowm.xchange.bybit.dto.BybitCategory;

public class BybitCancelOrderPayload {

  private BybitCategory category;
  private String symbol;
  private String orderId;
  private String orderLinkId;
  private String orderFilter;

  public BybitCancelOrderPayload(BybitCategory category, String symbol, String orderId, String orderLinkId) {
    this.category = category;
    this.symbol = symbol;
    this.orderId = orderId;
    this.orderLinkId = orderLinkId;
  }

}
