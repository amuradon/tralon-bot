package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cz.amuradon.tralon.agent.connector.Ticker;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcTicker(
		String symbol,
		BigDecimal priceChangePercent,
		BigDecimal lastPrice,
		BigDecimal openPrice,
		BigDecimal highPrice,
		BigDecimal lowPrice,
		BigDecimal volume,
		BigDecimal quoteVolume,
		long openTime,
		long closeTime,
		long count) implements Ticker {
	
	@Override
	public String toString() {
		return String.format("MexcTicker(%s, %d, %s, %s, %s)", symbol, closeTime, lastPrice, quoteVolume, volume);
	}

	@Override
	public BigDecimal weightedAvgPrice() {
		return lastPrice;  // FIXME nevypada, ze by v payloadu bylo
	}

	@Override
	public long firstId() {
		return 0; // Not supported
	}

	@Override
	public long lastId() {
		return 0; // Not supported
	}

	@Override
	public BigDecimal lastQty() {
		return BigDecimal.ZERO; // Not supported
	}

}
