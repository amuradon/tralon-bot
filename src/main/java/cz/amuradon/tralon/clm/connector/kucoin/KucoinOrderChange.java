package cz.amuradon.tralon.clm.connector.kucoin;

import java.math.BigDecimal;

import com.kucoin.sdk.websocket.event.OrderChangeEvent;

import cz.amuradon.tralon.clm.connector.OrderChange;

public class KucoinOrderChange implements OrderChange {

	private final OrderChangeEvent data;
	
	public KucoinOrderChange(OrderChangeEvent data) {
		this.data = data;
	}

	@Override
	public String getType() {
		return data.getType();
	}

	@Override
	public String getSymbol() {
		return data.getSymbol();
	}

	@Override
	public String getOrderId() {
		return data.getOrderId();
	}

	@Override
	public String getSide() {
		return data.getSide();
	}

	@Override
	public BigDecimal getSize() {
		return data.getSize();
	}

	@Override
	public BigDecimal getPrice() {
		return data.getPrice();
	}

	@Override
	public BigDecimal getRemainSize() {
		return data.getRemainSize();
	}

}
