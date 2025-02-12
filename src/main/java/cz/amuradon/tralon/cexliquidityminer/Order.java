package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;

public class Order {
	
	private final String orderId;
	private final Side side;
	private BigDecimal size;
	private BigDecimal price;
	
	public Order(String orderId,
			Side side,
			BigDecimal size,
			BigDecimal price) {
		this.orderId = orderId;
		this.side = side;
		this.size = size;
		this.price = price;
	}

	public String orderId() {
		return orderId;
	}
	
	public void size(BigDecimal size) {
		this.size = size;
	}
	
	public BigDecimal size() {
		return size;
	}
	
	public Side side() {
		return side;
	}
	
	public BigDecimal price() {
		return price;
	}
	
	@Override
	public String toString() {
		return String.format("Order { %s, %s, %f, %f}", orderId, side, size, price);
	}
}
