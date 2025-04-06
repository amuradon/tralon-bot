package cz.amuradon.tralon.clm.connector.binance;

public class BinanceUtils {

	public static String symbol(String baseAsset, String quoteAsset) {
		return baseAsset + quoteAsset;
	}
}
