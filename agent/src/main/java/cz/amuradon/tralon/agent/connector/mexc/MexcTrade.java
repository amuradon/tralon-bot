package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.mxc.push.common.protobuf.PublicAggreDealsV3ApiItem;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.Trade;

public class MexcTrade implements Trade {

	private final PublicAggreDealsV3ApiItem trade;
	
	public MexcTrade(PublicAggreDealsV3ApiItem trade) {
		this.trade = trade;
	}

	public BigDecimal price() {
		return new BigDecimal(trade.getPrice());
	}
	
	public BigDecimal quantity() {
		return new BigDecimal(trade.getQuantity());
	}
	
	public Side side() {
		return Side.values()[trade.getTradeType() - 1];
	}
	
	public long timestamp() {
		return trade.getTime();
	}
	
	@Override
	public String toString() {
		return String.format("%s{%s}", getClass().getSimpleName(), trade);
	}
}
