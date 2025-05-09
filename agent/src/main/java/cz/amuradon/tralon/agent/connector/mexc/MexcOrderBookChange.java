package cz.amuradon.tralon.agent.connector.mexc;

import java.util.List;
import java.util.stream.Collectors;

import com.mxc.push.common.protobuf.PublicAggreDepthsV3Api;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderBookUpdate;

/**
 * Used for Websocket update message.
 */
public class MexcOrderBookChange implements OrderBookChange {

	private final List<OrderBookUpdate> asks;
	private final List<OrderBookUpdate> bids;
	
	public MexcOrderBookChange(PublicAggreDepthsV3Api publicAggreDepths) {
		this.asks = publicAggreDepths.getAsksList().stream()
				.map(l -> new MexcOrderBookUpdate(l, Side.SELL, publicAggreDepths.getToVersion())).collect(Collectors.toList());
		this.bids = publicAggreDepths.getBidsList().stream()
				.map(l -> new MexcOrderBookUpdate(l, Side.BUY, publicAggreDepths.getToVersion())).collect(Collectors.toList());
	}

	@Override
	public List<OrderBookUpdate> getAsks() {
		return asks;
	}

	@Override
	public List<OrderBookUpdate> getBids() {
		return bids;
	}

	@Override
	public String toString() {
		return String.format("%s{asks=%s, bids=%s}", getClass().getSimpleName(), asks, bids);
	}
}
