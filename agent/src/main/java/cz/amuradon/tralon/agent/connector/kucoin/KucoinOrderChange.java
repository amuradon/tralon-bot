package cz.amuradon.tralon.agent.connector.kucoin;

import java.math.BigDecimal;

import com.kucoin.sdk.websocket.event.OrderChangeEvent;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.connector.OrderChange;

public class KucoinOrderChange implements OrderChange {

	private final OrderChangeEvent data;
	
	public KucoinOrderChange(OrderChangeEvent data) {
		this.data = data;
	}

	@Override
	public OrderStatus status() {
		if ("open".equalsIgnoreCase(data.getType())) {
			return OrderStatus.NEW;
		} else if ("match".equalsIgnoreCase(data.getType())) {
			return OrderStatus.PARTIALLY_FILLED;
		} else if ("filled".equalsIgnoreCase(data.getType())) {
			return OrderStatus.FILLED;
		} else if ("canceled".equalsIgnoreCase(data.getType())) {
			return OrderStatus.CANCELED;
		} else {
			// XXX return something not used?
			return OrderStatus.PENDING_CANCEL;
		}
	}

	@Override
	public String symbol() {
		return data.getSymbol();
	}

	@Override
	public String orderId() {
		return data.getOrderId();
	}

	@Override
	public String side() {
		return data.getSide();
	}

	@Override
	public BigDecimal size() {
		return data.getSize();
	}

	@Override
	public BigDecimal price() {
		return data.getPrice();
	}

	@Override
	public BigDecimal remainSize() {
		return data.getRemainSize();
	}

}
