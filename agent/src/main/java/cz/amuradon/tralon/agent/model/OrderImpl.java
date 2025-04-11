package cz.amuradon.tralon.agent.model;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.Side;

public class OrderImpl implements Order {
	
	private final String orderId;
	private final String symbol;
	private final Side side;
	private BigDecimal size;
	private BigDecimal price;
	
	public OrderImpl(String orderId,
			String symbol,
			Side side,
			BigDecimal size,
			BigDecimal price) {
		this.orderId = orderId;
		this.symbol = symbol;
		this.side = side;
		this.size = size;
		this.price = price;
	}

	@Override
	public String orderId() {
		return orderId;
	}
	
	@Override
	public String symbol() {
		return symbol;
	}
	
	@Override
	public void size(BigDecimal size) {
		this.size = size;
	}
	
	@Override
	public BigDecimal size() {
		return size;
	}
	
	@Override
	public Side side() {
		return side;
	}
	
	@Override
	public BigDecimal price() {
		return price;
	}
	
	@Override
	public String toString() {
		return String.format("Order { %s, %s, %s, %f, %f}", orderId, symbol, side, size, price);
	}

}
