package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.OrderStatus;

public interface OrderChange {

	OrderStatus status();

	String symbol();

	String orderId();

	String side();

	BigDecimal size();

	BigDecimal price();

	BigDecimal remainSize();

}
