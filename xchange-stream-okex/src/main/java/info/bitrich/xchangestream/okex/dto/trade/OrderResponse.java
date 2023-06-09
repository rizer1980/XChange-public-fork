package info.bitrich.xchangestream.okex.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class OrderResponse {
    private String id;
    private String op;
    private String code;
    private String message;
    List<OrderResponse.OrderData> args = new LinkedList<>();

    public static class OrderData {
        private String clOrdId;
        private String ordId;
        private String tag;
        private String sCode;
        private String sMsg;
    }

}
