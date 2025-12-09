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

	@Override
	public BigDecimal priceChangePercent() {
		return ticker.getChangeRate().multiply(new BigDecimal(100));
	}

	@Override
	public BigDecimal weightedAvgPrice() {
		return ticker.getAveragePrice();  // not clear if it's weighted
	}

	@Override
	public BigDecimal highPrice() {
		return ticker.getHigh();
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
	public long count() {
		return 0; // Not supported
	}

	@Override
	public BigDecimal lastQty() {
		return BigDecimal.ZERO; // Not supported
	}

	@Override
	public BigDecimal openPrice() {
		return BigDecimal.ZERO; // Not supported
	}

	@Override
	public BigDecimal lowPrice() {
		return BigDecimal.ZERO; // Not supported
	}

	@Override
	public long openTime() {
		return 0; // Not supported
	}

}
