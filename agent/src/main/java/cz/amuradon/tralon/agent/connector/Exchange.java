package cz.amuradon.tralon.agent.connector;

import java.lang.annotation.Annotation;

import cz.amuradon.tralon.agent.connector.binance.Binance;
import cz.amuradon.tralon.agent.connector.kucoin.Kucoin;
import cz.amuradon.tralon.agent.connector.mexc.Mexc;

public enum Exchange {

	BINANCE("Binance", Binance.LITERAL),
	KUCOIN("Kucoin", Kucoin.LITERAL) {
		@Override
		public String symbol(String baseAsset, String quoteAsset) {
			return baseAsset + "-" + quoteAsset;
		}
	},
	MEXC("MEXC", Mexc.LITERAL) {
		@Override
		public String getWebsocketDataFileExtesion() {
			return "pb";
		}
	};
	
	private final String displayName;
	private final Annotation qualifier;
	
	private Exchange(final String displayName, final Annotation qualifier) {
		this.displayName = displayName;
		this.qualifier = qualifier;
	}

	public String displayName() {
		return displayName;
	}

	public Annotation qualifier() {
		return qualifier;
	}
	
	public static Exchange fromDisplayName(String displayName) {
		for (Exchange exchange : values()) {
			if (displayName.equals(exchange.displayName)) {
				return exchange;
			}
		}
		throw new IllegalArgumentException("Could not find exchange " + displayName);
	}
	
	public String symbol(String baseAsset, String quoteAsset) {
		return baseAsset + quoteAsset;
	}

	public String getWebsocketDataFileExtesion() {
		return "json";
	}
}
