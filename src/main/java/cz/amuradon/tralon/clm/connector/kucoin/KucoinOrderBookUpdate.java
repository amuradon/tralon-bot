package cz.amuradon.tralon.clm.connector.kucoin;

import java.math.BigDecimal;
import java.util.List;

import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

public class KucoinOrderBookUpdate implements OrderBookUpdate {

	private final long sequence;
	private final BigDecimal price;
	private final BigDecimal size;
	private final Side side;
	
	public KucoinOrderBookUpdate(final List<String> data, final Side side) {
		sequence = Long.parseLong(data.get(2));
		price = new BigDecimal(data.get(0));
		size = new BigDecimal(data.get(1));
		this.side = side;
	}
	
	@Override
	public long sequence() {
		return sequence;
	}

	@Override
	public BigDecimal price() {
		return price;
	}

	@Override
	public BigDecimal size() {
		return size;
	}

	@Override
	public Side side() {
		return side;
	}


}
