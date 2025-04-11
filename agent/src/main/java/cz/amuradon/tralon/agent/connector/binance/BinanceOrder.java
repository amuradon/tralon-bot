package cz.amuradon.tralon.agent.connector.binance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.model.Order;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceOrder implements Order {

	private String orderId;
	private String symbol;
	private BigDecimal origQty;
	private Side side;
	private BigDecimal price;

	@JsonCreator
	public BinanceOrder(@JsonProperty("orderId") String orderId,
			@JsonProperty("symbol") String symbol,
			@JsonProperty("origQty") BigDecimal origQty,
			@JsonProperty("side") Side side,
			@JsonProperty("price") BigDecimal price) {
		this.orderId = orderId;
		this.symbol = symbol;
		this.origQty = origQty;
		this.side = side;
		this.price = price;
	}
	
	@Override
	public String orderId() {
		return orderId;
	}
	

	@Override
	public String symbol() {
		return symbol;
	}

	@Override
	public void size(BigDecimal size) {
		this.origQty = size;
	}

	@Override
	public BigDecimal size() {
		return origQty;
	}

	@Override
	public Side side() {
		return side;
	}

	@Override
	public BigDecimal price() {
		return price;
	}

	@Override
	public String toString() {
		return String.format("Order { %s, %s, %s, %f, %f}", orderId, symbol, side, origQty, price);
	}
}
