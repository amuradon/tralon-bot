package cz.amuradon.tralon.agent.connector.kucoin;

import java.math.BigDecimal;

import com.kucoin.sdk.rest.response.MarketTickerResponse;

import cz.amuradon.tralon.agent.connector.Ticker;

public class KucoinTicker implements Ticker {
	
	private final MarketTickerResponse ticker;
	private final long time;

	public KucoinTicker(MarketTickerResponse ticker, long time) {
		this.ticker = ticker;
		this.time = time;
	}

	@Override
	public String symbol() {
		return ticker.getSymbol();
	}

	public long closeTime() {
		return time;
	}

	@Override
	public BigDecimal lastPrice() {
		return ticker.getLast();
	}

	@Override
	public BigDecimal quoteVolume() {
		return ticker.getVolValue();
	}

	public BigDecimal volume() {
		return ticker.getVol();
	}

}
