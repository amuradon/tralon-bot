package cz.amuradon.tralon.agent.connector.mexc;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderBookUpdate;

/**
 * Used for Websocket update message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcOrderBookChange implements OrderBookChange {

	private final List<OrderBookUpdate> asks;
	private final List<OrderBookUpdate> bids;
	
	@JsonCreator
	public MexcOrderBookChange(
			@JsonProperty("r") long lastUpdateId,
			@JsonProperty("asks") List<MexcOrderBookChangeRecord> asks,
			@JsonProperty("bids") List<MexcOrderBookChangeRecord> bids) {
		this.asks = asks.stream()
				.map(l -> new MexcOrderBookUpdate(l, Side.SELL, lastUpdateId)).collect(Collectors.toList());
		this.bids = bids.stream()
				.map(l -> new MexcOrderBookUpdate(l, Side.BUY, lastUpdateId)).collect(Collectors.toList());
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
