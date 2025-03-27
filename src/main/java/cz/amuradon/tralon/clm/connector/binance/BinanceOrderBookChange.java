package cz.amuradon.tralon.clm.connector.binance;

import java.util.List;

import cz.amuradon.tralon.clm.connector.OrderBookChange;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

/**
 * Used for Websocket update message.
 */
public class BinanceOrderBookChange implements OrderBookChange {

	@Override
	public List<OrderBookUpdate> getAsks() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<OrderBookUpdate> getBids() {
		// TODO Auto-generated method stub
		return null;
	}

}
