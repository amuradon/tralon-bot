package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import cz.amuradon.tralon.clm.OrderType;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.model.Order;

public interface RestClient {

	NewOrderBuilder newOrder();
	
	void cancelOrder(Order order);
	
	Map<String, Order> listOrders(String symbol);
	
	List<? extends AccountBalance> listBalances();
			
	interface NewOrderBuilder {
		NewOrderBuilder clientOrderId(String clientOrderId);
		NewOrderBuilder side(Side side);
		NewOrderBuilder symbol(String symbol);
		NewOrderBuilder price(BigDecimal price);
		NewOrderBuilder size(BigDecimal size);
		NewOrderBuilder type(OrderType type);
		String send();
	}
}
