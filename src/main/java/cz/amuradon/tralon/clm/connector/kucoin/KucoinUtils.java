package cz.amuradon.tralon.clm.connector.kucoin;

public class KucoinUtils {

	public static String symbol(String baseAsset, String quoteAsset) {
		return baseAsset + "-" + quoteAsset;
	}
}
