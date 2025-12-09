package cz.amuradon.tralon.agent.connector.binance.alpha;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.connector.Ticker;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAlphaTicker(String symbol,
		@JsonProperty("price") BigDecimal lastPrice,
		@JsonProperty("priceHigh24h") BigDecimal highPrice,
		@JsonProperty("priceLow24h") BigDecimal lowPrice,
		@JsonProperty("volume24h") BigDecimal quoteVolume,
		@JsonProperty("percentChange24h") BigDecimal priceChangePercent,
		@JsonProperty("count24h") long count,
		boolean listingCex,
		String chainName,
		String contractAddress) implements Ticker {
	// XXX volumes jsou nejaky divny
	
	@Override
	public String toString() {
		return String.format("BinanceAlphaTicker(%s, %s, %s)", symbol, lastPrice, quoteVolume);
	}

	@Override
	public BigDecimal weightedAvgPrice() {
		return BigDecimal.ZERO;  // Not on token list
	}

	@Override
	public BigDecimal volume() {
		return BigDecimal.ZERO; // Not on token list
	}

	@Override
	public long firstId() {
		return 0; // Not on token list
	}

	@Override
	public long lastId() {
		return 0; // Not on token list
	}

	@Override
	public BigDecimal lastQty() {
		return null;  // Not on token list
	}

	@Override
	public BigDecimal openPrice() {
		return BigDecimal.ZERO; // Not on token list
	}

	@Override
	public long openTime() {
		return 0; // Not on token list
	}

	@Override
	public long closeTime() {
		return 0; // Not on token list
	}
}
