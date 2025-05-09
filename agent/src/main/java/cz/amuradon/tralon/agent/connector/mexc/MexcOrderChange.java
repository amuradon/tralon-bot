package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.mxc.push.common.protobuf.PrivateOrdersV3Api;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.OrderChange;

/**
 * Used for Websocket update message.
 */
public class MexcOrderChange implements OrderChange {

	private final String symbol;
	private final PrivateOrdersV3Api privateOrders;
	
	public MexcOrderChange(String symbol, PrivateOrdersV3Api privateOrders) {
		this.symbol = symbol;
		this.privateOrders = privateOrders;
	}

	public String symbol() {
		return symbol;
	}

	public OrderStatus status() {
		return switch(privateOrders.getStatus()) {
		case 1 -> OrderStatus.NEW; 
		case 2 -> OrderStatus.FILLED; 
		case 3 -> OrderStatus.PARTIALLY_FILLED; 
		case 4 -> OrderStatus.CANCELED; 
		case 5 -> OrderStatus.CANCELED; 
		default -> OrderStatus.PENDING_CANCEL;
	};
	}
	
	public String orderId() {
		return privateOrders.getId();
	}

	public String clientOrderId() {
		return privateOrders.getClientId();
	}

	public String side() {
		return Side.values()[privateOrders.getTradeType() - 1].name();
	}

	public BigDecimal price() {
		return new BigDecimal(privateOrders.getPrice());
	}

	public BigDecimal quantity() {
		return new BigDecimal(privateOrders.getQuantity());
	}

	public BigDecimal cumulativeQuantity() {
		return new BigDecimal(privateOrders.getCumulativeQuantity());
	}

	public BigDecimal remainingQuantity() {
		return new BigDecimal(privateOrders.getRemainQuantity());
	}
	
	@Override
	public String toString() {
		return String.format("%s{%s}", getClass().getSimpleName(), privateOrders);
	}

}
