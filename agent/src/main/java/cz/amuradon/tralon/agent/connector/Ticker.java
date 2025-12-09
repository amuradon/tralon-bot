package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

public interface Ticker {
		
	String symbol();
	BigDecimal priceChangePercent();
	BigDecimal weightedAvgPrice();
	BigDecimal lastPrice();
	BigDecimal lastQty();
	BigDecimal openPrice();
	BigDecimal highPrice();
	BigDecimal lowPrice();
	BigDecimal volume();
	BigDecimal quoteVolume();
	long openTime();
	long closeTime();
	long firstId();
	long lastId();
	long count();
	
}
