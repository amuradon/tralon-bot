package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.connector.OrderChange;

/**
 * Used for Websocket update message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcOrderChange implements OrderChange {

	private OrderStatus status;
	
	@JacksonInject("symbol")
	private String symbol;
	
	private final String orderId;
	private final String clientOrderId;
	private final String side;
	private final BigDecimal price;
	private final BigDecimal quantity;
	private final BigDecimal cumulativeQuantity;
	private final BigDecimal remainingQuantity;
	
	public MexcOrderChange(
			@JsonProperty("i") String orderId,
			@JsonProperty("c") String clientOrderId,
			@JsonProperty("S") String side,
			@JsonProperty("p") BigDecimal price,
			@JsonProperty("v") BigDecimal quantity,
			@JsonProperty("cv") BigDecimal cumulativeQuantity,
			@JsonProperty("V") BigDecimal remainingQuantity) {
		this.orderId = orderId;
		this.clientOrderId = clientOrderId;
		this.side = side;
		this.price = price;
		this.quantity = quantity;
		this.cumulativeQuantity = cumulativeQuantity;
		this.remainingQuantity = remainingQuantity;
	}

	public String symbol() {
		return symbol;
	}

	public OrderStatus status() {
		return status;
	}
	
	@JsonProperty("s")
	public void status(int value) {
		this.status = switch(value) {
			case 1 -> OrderStatus.NEW; 
			case 2 -> OrderStatus.FILLED; 
			case 3 -> OrderStatus.PARTIALLY_FILLED; 
			case 4 -> OrderStatus.CANCELED; 
			case 5 -> OrderStatus.CANCELED; 
			default -> OrderStatus.PENDING_CANCEL;
		};
	}

	public String orderId() {
		return orderId;
	}

	public String clientOrderId() {
		return clientOrderId;
	}

	public String side() {
		return side;
	}

	public BigDecimal price() {
		return price;
	}

	public BigDecimal quantity() {
		return quantity;
	}

	public BigDecimal cumulativeQuantity() {
		return cumulativeQuantity;
	}

	public BigDecimal remainingQuantity() {
		return remainingQuantity;
	}
	
	@Override
	public String toString() {
		return String.format("%s{orderId=%s, clientOrderId=%s, status=%s, symbol=%s, side=%s, price=%s,"
				+ " quantity=%s, cumulativeQuantity=%s, remainingQuantity=%s}", getClass().getSimpleName(),
				orderId, clientOrderId, status, symbol, side, price, quantity, cumulativeQuantity, remainingQuantity);
	}

}
