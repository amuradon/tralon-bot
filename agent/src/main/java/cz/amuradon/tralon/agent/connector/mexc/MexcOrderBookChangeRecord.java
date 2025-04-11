package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcOrderBookChangeRecord(
		@JsonProperty("p") BigDecimal price,
		@JsonProperty("v") BigDecimal volume) {

}
