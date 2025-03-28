package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public class OrderBookResponseImpl implements OrderBookResponse {

	private final long sequence;
	private final Map<BigDecimal, BigDecimal> asks;
	private final Map<BigDecimal, BigDecimal> bids;
	
	public OrderBookResponseImpl(final long sequence, final List<List<String>> asks, final List<List<String>> bids) {
		this.sequence = sequence;
		this.asks = parse(asks, Comparator.naturalOrder());
		this.bids = parse(bids, Comparator.reverseOrder());
	}
	
	@Override
	public long sequence() {
		return sequence;
	}

	@Override
	public Map<BigDecimal, BigDecimal> asks() {
		return asks;
	}

	@Override
	public Map<BigDecimal, BigDecimal> bids() {
		return bids;
	}

	private Map<BigDecimal, BigDecimal> parse(List<List<String>> list, final Comparator<BigDecimal> comparator) {
		final Map<BigDecimal, BigDecimal> map = new ConcurrentSkipListMap<>(comparator);
    	for (List<String> element : list) {
			map.put(new BigDecimal(element.get(0)), new BigDecimal(element.get(1)));
		}
    	return map;
    }
}
