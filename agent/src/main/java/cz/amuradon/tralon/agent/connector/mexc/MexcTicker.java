package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cz.amuradon.tralon.agent.connector.Ticker;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcTicker(String symbol,
		long closeTime,
		BigDecimal lastPrice,
		BigDecimal quoteVolume,
		BigDecimal volume) implements Ticker {
	
	@Override
	public String toString() {
		return String.format("MexcTicker(%s, %d, %s, %s, %s)", symbol, closeTime, lastPrice, quoteVolume, volume);
	}
}
