package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.connector.OrderChange;

/**
 * Used for Websocket update message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcOrderChange(
		@JsonProperty("S") OrderStatus status, // XXX this will probably need converter from int to Enum
		@JacksonInject String symbol,
		@JsonProperty("i") String orderId,
		@JsonProperty("S") String side,
		@JsonProperty("v") BigDecimal size,
		@JsonProperty("p") BigDecimal price,
		@JsonProperty("V") BigDecimal remainSize) implements OrderChange {

}
