package cz.amuradon.tralon.agent.model;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.Side;

public interface Order {
	
	String orderId();
	
	String symbol();
	
	void size(BigDecimal size);
	
	BigDecimal size();
	
	Side side();
	
	BigDecimal price();
	
}
