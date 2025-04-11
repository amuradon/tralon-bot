package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.Side;

public interface Trade {

	BigDecimal price();
	Side side();
	BigDecimal quantity();
	long timestamp();
}
