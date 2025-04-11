package cz.amuradon.tralon.agent.connector.binance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.Trade;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanaceTrade(
		@JsonProperty("p") BigDecimal price,
		@JsonProperty("S") Side side, // XXX would this fail? It seems Binance does not have it.
		@JsonProperty("q") BigDecimal quantity,
		@JsonProperty("T") long timestamp) implements Trade {

}
