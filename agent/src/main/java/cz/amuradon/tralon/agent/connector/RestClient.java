package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.model.Order;

public interface RestClient {

	void cancelOrder(String orderId, String symbol);
	
	Map<String, Order> listOrders(String symbol);
	
	List<? extends AccountBalance> listBalances();

	OrderBookResponse orderBook(String symbol);

	SymbolInfo cacheSymbolDetails(String symbol);
	
	NewOrderSymbolBuilder newOrder();

	String userDataStream();
	
	Ticker[] ticker();
	
	void setListener(RestClientListener listener);

	interface NewOrderBuilder {
		NewOrderBuilder clientOrderId(String clientOrderId);
		NewOrderBuilder side(Side side);
		NewOrderBuilder price(BigDecimal price);
		NewOrderBuilder size(BigDecimal size);
		NewOrderBuilder type(OrderType type);
		NewOrderBuilder timestamp(long timestamp);
    	NewOrderBuilder recvWindow(long recvWindow);
    	NewOrderBuilder signParams();
		String send();
	}
	
	interface NewOrderSymbolBuilder {
		NewOrderBuilder symbol(String symbol);
	}

}
