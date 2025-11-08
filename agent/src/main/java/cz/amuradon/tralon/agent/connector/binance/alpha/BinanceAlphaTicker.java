package cz.amuradon.tralon.agent.connector.binance.alpha;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.connector.Ticker;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAlphaTicker(String symbol,
		@JsonProperty("price") BigDecimal lastPrice,
		@JsonProperty("volume24h") BigDecimal quoteVolume,
		boolean listingCex,
		@JsonProperty("percentChange24h") BigDecimal priceChangePercent) implements Ticker {
	// XXX volumes jsou nejaky divny
	
	@Override
	public String toString() {
		return String.format("BinanceAlphaTicker(%s, %s, %s)", symbol, lastPrice, quoteVolume);
	}

	@Override
	public BigDecimal weightedAvgPrice() {
		return lastPrice;  // FIXME nevypada, ze by v payloadu bylo
	}
}
