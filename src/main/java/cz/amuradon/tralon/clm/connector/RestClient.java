package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import cz.amuradon.tralon.clm.Order;
import cz.amuradon.tralon.clm.Side;

public interface RestClient {

	NewOrderBuilder newOrder();
	
	void cancelOrder(String orderId);
	
	Map<String, Order> listOrders(String symbol);
	
	List<AccountBalance> listBalances();
			
	interface NewOrderBuilder {
		NewOrderBuilder clientOrderId(String clientOrderId);
		NewOrderBuilder side(Side side);
		NewOrderBuilder symbol(String symbol);
		NewOrderBuilder price(BigDecimal price);
		NewOrderBuilder size(BigDecimal size);
		NewOrderBuilder type(String type);
		String send();
	}
}
