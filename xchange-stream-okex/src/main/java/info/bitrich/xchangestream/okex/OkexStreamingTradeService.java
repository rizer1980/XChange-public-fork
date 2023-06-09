package info.bitrich.xchangestream.okex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.okex.dto.OkexLoginMessage;
import info.bitrich.xchangestream.okex.dto.trade.OrderRequest;
import info.bitrich.xchangestream.okex.dto.trade.OrderResponse;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.Observable;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexAdapters;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;
import org.knowm.xchange.okex.dto.trade.OkexOrderFlags;
import org.knowm.xchange.okex.dto.trade.OkexOrderType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import static info.bitrich.xchangestream.okex.OkexStreamingService.USERTRADES;
import static org.knowm.xchange.okex.OkexAdapters.adaptTradeMode;
import static org.knowm.xchange.okex.OkexAdapters.convertVolumeToContractSize;

public class OkexStreamingTradeService implements StreamingTradeService {

    private final OkexStreamingService service;
    private final ExchangeMetaData exchangeMetaData;
    private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
    private String accountLevel;

    public OkexStreamingTradeService(OkexStreamingService service, ExchangeMetaData exchangeMetaData, String accountLevel) {
        this.service = service;
        this.exchangeMetaData = exchangeMetaData;
        this.accountLevel = accountLevel;
    }

    @Override
    public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
        String channelUniqueId = USERTRADES+OkexAdapters.adaptInstrument(instrument);

        return service.subscribeChannel(channelUniqueId)
                .filter(message-> message.has("data"))
                .flatMap(jsonNode -> {
                    List<OkexOrderDetails> okexOrderDetails = mapper.treeToValue(jsonNode.get("data"), mapper.getTypeFactory().constructCollectionType(List.class, OkexOrderDetails.class));
                    return Observable.fromIterable(OkexAdapters.adaptUserTrades(okexOrderDetails, exchangeMetaData).getUserTrades());
                }
        );
    }
    public void placeLimitOrder(LimitOrder limitOrder) throws IOException {
        List<OrderRequest.OrderArg> args = new LinkedList<>();
        String amount = convertVolumeToContractSize(limitOrder, exchangeMetaData);
        String orderType = (limitOrder.hasFlag(OkexOrderFlags.POST_ONLY))
                ? OkexOrderType.post_only.name()
                : (limitOrder.hasFlag(OkexOrderFlags.OPTIMAL_LIMIT_IOC) && limitOrder.getInstrument() instanceof FuturesContract)
                ? OkexOrderType.optimal_limit_ioc.name()
                : OkexOrderType.limit.name();
        args.add(new OrderRequest.OrderArg(limitOrder.getType() == Order.OrderType.BID ? "buy" : "sell",
                OkexAdapters.adaptInstrument(limitOrder.getInstrument()),
                        adaptTradeMode(limitOrder.getInstrument(),accountLevel),
                        orderType, amount));
        OrderRequest message = new OrderRequest("testId","order",args);
        String payload = StreamingObjectMapperHelper.getObjectMapper().writeValueAsString(message);
        service.sendMessage(payload);
        //
    }
}
