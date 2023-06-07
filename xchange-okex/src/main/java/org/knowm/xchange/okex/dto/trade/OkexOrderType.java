package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.dto.Order;

public enum OkexOrderType implements Order.IOrderFlags {
    market,
    limit,
    post_only,
    fok,
    iok,
    optimal_limit_ioc
}
