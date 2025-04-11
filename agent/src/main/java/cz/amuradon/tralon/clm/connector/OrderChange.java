package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.clm.OrderStatus;

public interface OrderChange {

	OrderStatus status();

	String symbol();

	String orderId();

	String side();

	BigDecimal size();

	BigDecimal price();

	BigDecimal remainSize();

}
