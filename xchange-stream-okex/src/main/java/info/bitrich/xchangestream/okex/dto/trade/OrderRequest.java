package info.bitrich.xchangestream.okex.dto.trade;

import info.bitrich.xchangestream.okex.dto.OkexLoginMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;

@Getter @Setter @AllArgsConstructor
public class OrderRequest {
    private String id;
    private String op;

    List<OrderArg> args;

    @Getter @Setter @AllArgsConstructor
    public static class OrderArg {
        private String side;
        private String instId;
        private String tdMode;
        private String ordType;
        private String sz;
    }
}
