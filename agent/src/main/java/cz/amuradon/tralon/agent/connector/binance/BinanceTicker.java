package cz.amuradon.tralon.agent.connector.binance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cz.amuradon.tralon.agent.connector.Ticker;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTicker(
		String symbol,
		BigDecimal priceChangePercent,
		BigDecimal weightedAvgPrice,
		BigDecimal lastPrice,
		BigDecimal lastQty,
		BigDecimal openPrice,
		BigDecimal highPrice,
		BigDecimal lowPrice,
		BigDecimal volume,
		BigDecimal quoteVolume,
		long openTime,
		long closeTime,
		long firstId,
		long lastId,
		long count) implements Ticker {
	
}
