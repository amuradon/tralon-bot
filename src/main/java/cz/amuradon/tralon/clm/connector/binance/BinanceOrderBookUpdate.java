package cz.amuradon.tralon.clm.connector.binance;

import java.math.BigDecimal;

import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

/**
 * Used for Websocket update message.
 */
public class BinanceOrderBookUpdate implements OrderBookUpdate {

	@Override
	public long sequence() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BigDecimal price() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BigDecimal size() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Side side() {
		// TODO Auto-generated method stub
		return null;
	}

}
