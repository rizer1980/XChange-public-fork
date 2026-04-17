package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public class OkexClosePositionRequest {
    @JsonProperty("instId")
    private String instrumentId;

    @JsonProperty("posSide")
    private String posSide;

    @JsonProperty("mgnMode")
    private String mgnMode;

    @JsonProperty("ccy")
    private String ccy;

    @JsonProperty("autoCxl")
    private boolean autoCxl;

    @JsonProperty("clOrdId")
    private String clOrdId;

    @JsonProperty("tag")
    private String tag;
}
