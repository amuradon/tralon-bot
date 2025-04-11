package cz.amuradon.tralon.agent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class OrderBook { 
	
	private long sequence;
	
	private Map<BigDecimal, BigDecimal> asks;
	private Map<BigDecimal, BigDecimal> bids;
	private final Map<Side, Map<BigDecimal, BigDecimal>> sides;
	
	public OrderBook() {
		this.sides = new HashMap<>();
	}
	
	public long sequence() {
		return sequence;
	}

	public void setSequence(long sequence) {
		this.sequence = sequence;
	}

	public Map<BigDecimal, BigDecimal> getAsks() {
		return asks;
	}
	
	public Map<BigDecimal, BigDecimal> getBids() {
		return bids;
	}

	public Map<BigDecimal, BigDecimal> getOrderBookSide(Side side) {
		return sides.get(side);
	}
	
	public void setAsks(Map<BigDecimal, BigDecimal> asks) {
		this.asks = asks;
		sides.put(Side.SELL, asks);
	}
	
	public void setBids(Map<BigDecimal, BigDecimal> bids) {
		this.bids = bids;
		sides.put(Side.BUY, bids);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("Sequence: ").append(sequence).append("\nAsks:\n");
		asks.entrySet().stream().limit(10).forEach(e -> builder.append(e).append("\n"));
		builder.append("Bids:\n");
		bids.entrySet().stream().limit(10).forEach(e -> builder.append(e).append("\n"));
		
		return builder.toString();
	}

}
