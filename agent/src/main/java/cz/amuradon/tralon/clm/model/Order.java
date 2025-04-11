package cz.amuradon.tralon.clm.model;

import java.math.BigDecimal;

import cz.amuradon.tralon.clm.Side;

public interface Order {
	
	String orderId();
	
	String symbol();
	
	void size(BigDecimal size);
	
	BigDecimal size();
	
	Side side();
	
	BigDecimal price();
	
}
