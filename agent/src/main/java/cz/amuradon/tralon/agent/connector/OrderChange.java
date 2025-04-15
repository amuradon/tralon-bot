package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.OrderStatus;

public interface OrderChange {

	OrderStatus status();

	String symbol();

	String orderId();
	
	String clientOrderId();

	String side();

	BigDecimal quantity();

	BigDecimal price();

	BigDecimal remainingQuantity();
	
	BigDecimal cumulativeQuantity();

}
