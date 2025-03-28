package cz.amuradon.tralon.clm.connector.binance;

import java.math.BigDecimal;
import java.util.List;

import cz.amuradon.tralon.clm.OrderBook;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

/**
 * Used for Websocket update message.
 */
public class BinanceOrderBookUpdate implements OrderBookUpdate {

	private final long lastUpdateId;
	private final BigDecimal price;
	private final BigDecimal size;
	private final Side side;
	
	public BinanceOrderBookUpdate(List<String> data, Side side, long lastUpdateId) {
		this.lastUpdateId = lastUpdateId;
		price = new BigDecimal(data.get(0));
		size = new BigDecimal(data.get(1));
		this.side = side;
	}

	@Override
	public long sequence() {
		return lastUpdateId;
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

	@Override
	public boolean setSequenceIfShouldBeProcessed(OrderBook orderBook) {
		// Process updates with the same last update ID
		if (lastUpdateId >= orderBook.sequence()) {
			orderBook.setSequence(lastUpdateId);
			return true;
		} else {
			return false;
		}
	}

}
