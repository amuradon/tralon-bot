package cz.amuradon.tralon.agent.connector;

import java.lang.annotation.Annotation;

import org.eclipse.microprofile.config.ConfigProvider;

import cz.amuradon.tralon.agent.connector.binance.Binance;
import cz.amuradon.tralon.agent.connector.binance.alpha.BinanceAlpha;
import cz.amuradon.tralon.agent.connector.binance.alpha.BinanceAlphaTicker;
import cz.amuradon.tralon.agent.connector.binance.futures.BinanceFutures;
import cz.amuradon.tralon.agent.connector.kucoin.Kucoin;
import cz.amuradon.tralon.agent.connector.mexc.Mexc;

public enum Exchange {

	BINANCE("Binance", Binance.LITERAL, "USDC"),  // USDT is blocked for EU
	BINANCE_ALPHA("Binance Alpha", BinanceAlpha.LITERAL) {
		@Override
		public boolean momentumTokenfilter(Ticker ticker) {
			return !((BinanceAlphaTicker) ticker).listingCex(); 
		}
		
		@Override
		String exchangeLinkUrlPath(Ticker ticker) {
			BinanceAlphaTicker casted = (BinanceAlphaTicker) ticker;
			return casted.chainName().toLowerCase() + "/" + casted.contractAddress();
		}
	},
	// For Futures USDT is allowed in EU
	BINANCE_FUTURES("Binance Futures", BinanceFutures.LITERAL) {
		String tradingViewLinkSymbol(String symbol) {
			return String.format("BINANCE:%s.P", symbol);
		}
	},  
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
	private final String quoteToken;
	
	private Exchange(final String displayName, final Annotation qualifier) {
		this(displayName, qualifier, "USDT");
	}

	private Exchange(final String displayName, final Annotation qualifier, final String quoteToken) {
		this.displayName = displayName;
		this.qualifier = qualifier;
		this.quoteToken = quoteToken;
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
	
	public boolean momentumTokenfilter(Ticker ticker) {
		return ticker.symbol().endsWith(quoteToken);
	}

	public String exchangeLink(Ticker ticker) {
		return String.format("%s/%s",
				ConfigProvider.getConfig().getValue("exchanges." + name().toLowerCase() + ".baseUrl", String.class),
				exchangeLinkUrlPath(ticker));
	}

	public String tradingViewLink(Ticker ticker) {
		return String.format("https://www.tradingview.com/chart/?symbol=%s",
				tradingViewLinkSymbol(ticker.symbol()));
	}
	
	String exchangeLinkUrlPath(Ticker ticker) {
		return ticker.symbol();
	}

	String tradingViewLinkSymbol(String symbol) {
		return String.format("%s:%s", displayName.toUpperCase(), symbol);
	}

}
