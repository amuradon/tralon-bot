package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.Trade;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcTrade(
		@JsonProperty("p") BigDecimal price,
		@JsonProperty("v") BigDecimal quantity,
		@JsonProperty("S") Side side,
		@JsonProperty("t") long timestamp
		) implements Trade {

}
