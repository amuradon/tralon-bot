package cz.amuradon.tralon.clm.connector.kucoin;

import java.util.List;
import java.util.stream.Collectors;

import com.kucoin.sdk.websocket.event.Level2ChangeEvent;

import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.OrderBookChange;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

public class KucoinOrderBookChange implements OrderBookChange {

	private final List<OrderBookUpdate> asks;
	private final List<OrderBookUpdate> bids;
	
	public KucoinOrderBookChange(Level2ChangeEvent data) {
		asks = data.getChanges().getAsks().stream()
				.map(l -> new KucoinOrderBookUpdate(l, Side.SELL)).collect(Collectors.toList());
		bids = data.getChanges().getBids().stream()
				.map(l -> new KucoinOrderBookUpdate(l, Side.BUY)).collect(Collectors.toList());
	}

	@Override
	public List<OrderBookUpdate> getAsks() {
		return asks;
	}

	@Override
	public List<OrderBookUpdate> getBids() {
		return bids;
	}

}
