package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;
import java.util.Map;

public class OrderBook { 
	
	private long sequence;
	
	private final Map<BigDecimal, BigDecimal> asks;
	private final Map<BigDecimal, BigDecimal> bids;
	
	public OrderBook(long sequence,
		Map<BigDecimal, BigDecimal> asks,
		Map<BigDecimal, BigDecimal> bids) {
		this.sequence = sequence;
		this.asks = asks;
		this.bids = bids;
	}
	
	public long sequence() {
		return sequence;
	}

	public void sequence(long sequence) {
		this.sequence = sequence;
	}

	public Map<BigDecimal, BigDecimal> getAsks() {
		return asks;
	}

	public Map<BigDecimal, BigDecimal> getBids() {
		return bids;
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
