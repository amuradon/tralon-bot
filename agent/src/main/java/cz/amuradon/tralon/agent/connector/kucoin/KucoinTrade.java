package cz.amuradon.tralon.agent.connector.kucoin;

import java.math.BigDecimal;

import com.kucoin.sdk.websocket.event.Level3Event;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.Trade;

public class KucoinTrade implements Trade {

	private final BigDecimal price;
	private final Side side;
	private final BigDecimal quantity;
	private final long timestamp;
	
	public KucoinTrade(Level3Event data) {
		price = data.getPrice();
		side = Side.getValue(data.getSide());
		quantity = data.getSize();
		timestamp = data.getTs(); // XXX should not be getOrderTime()
	}

	@Override
	public BigDecimal price() {
		return price;
	}

	@Override
	public Side side() {
		return side;
	}

	@Override
	public BigDecimal quantity() {
		return quantity;
	}

	@Override
	public long timestamp() {
		return timestamp;
	}


}
