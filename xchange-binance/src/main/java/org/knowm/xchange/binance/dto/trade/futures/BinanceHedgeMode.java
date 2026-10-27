package org.knowm.xchange.binance.dto.trade.futures;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.dto.Order;

@Getter
@AllArgsConstructor
public enum BinanceHedgeMode implements Order.IOrderFlags {
  ONEWAY,
  TWOWAY;
}
