package cz.amuradon.tralon.cexliquiditymining;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderBook { 
	
	private long sequence;
	
	private final Map<BigDecimal, BigDecimal> asks;
	private final Map<BigDecimal, BigDecimal> bids;
	private final Map<Side, Map<BigDecimal, BigDecimal>> sides;
	
	public OrderBook() {
		this.asks = new ConcurrentSkipListMap<>(Comparator.naturalOrder());
		this.bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
		this.sides = new HashMap<>();
		sides.put(Side.SELL, asks);
		sides.put(Side.BUY, bids);
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
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("Sequence: ").append(sequence).append("\nAsks:\n");
		asks.entrySet().stream().limit(10).forEach(e -> builder.append(e).append("\n"));
		builder.append("Bids:\n");
		bids.entrySet().stream().limit(10).forEach(e -> builder.append(e).append("\n"));
		
		return builder.toString();
	}
}
