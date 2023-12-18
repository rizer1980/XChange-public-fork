package org.knowm.xchange.okex.dto;

import org.knowm.xchange.dto.Order.IOrderFlags;

public enum OkexInstType implements IOrderFlags {
  SPOT,
  MARGIN,
  SWAP,
  FUTURES,
  OPTION,
  ANY
}
