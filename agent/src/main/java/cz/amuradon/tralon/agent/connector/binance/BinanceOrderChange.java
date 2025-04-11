package cz.amuradon.tralon.agent.connector.binance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.connector.OrderChange;

/**
 * Used for Websocket update message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceOrderChange(
		@JsonProperty("X") OrderStatus status,
		@JsonProperty("s") String symbol,
		@JsonProperty("i") String orderId,
		@JsonProperty("S") String side,
		@JsonProperty("q") BigDecimal size,
		@JsonProperty("p") BigDecimal price,
		@JsonProperty("z") BigDecimal cumulativeFilledQuantity) implements OrderChange {

	@Override
	public BigDecimal remainSize() {
		return size.subtract(cumulativeFilledQuantity);
	}
}
