package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.dto.Order;

public enum OkexFuturePosSideFlag implements Order.IOrderFlags {
    LONG,
    SHORT;
}
