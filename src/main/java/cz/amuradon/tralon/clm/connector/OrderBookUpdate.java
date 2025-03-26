package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.clm.Side;

public interface OrderBookUpdate {

	long sequence();
	BigDecimal price();
	BigDecimal size();
	Side side();
}
