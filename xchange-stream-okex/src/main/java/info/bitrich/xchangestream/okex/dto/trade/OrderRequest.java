package info.bitrich.xchangestream.okex.dto.trade;

import info.bitrich.xchangestream.okex.dto.OkexLoginMessage;
import lombok.*;

import java.util.LinkedList;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderRequest {
    String id;
    String op;

    List<OrderArg> args;

    @Getter
    @Setter
    public static class OrderArg {
        String side;
        String instId;
        String tdMode;
        String ordType;
        String sz;
        String px;
        String posSide;

        public OrderArg(String side, String instId, String tdMode, String ordType, String sz) {
            this.side = side;
            this.instId = instId;
            this.tdMode = tdMode;
            this.ordType = ordType;
            this.sz = sz;
        }
    }
}
