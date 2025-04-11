package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import io.quarkus.logging.Log;


// TODO should be split to different strategies (?) like SpreadStrategy for market making
// ^^^ Neni to prilis omezujici? Jestli ten "String programming" neni flexibilnejsi, ale i chybovejsi?
public class ComputeInitialPrice {

	public static final String BUY_ORDER_LIMIT_PRICE_PROP_NAME = "buyOrder.price";

	public static final String BEAN_NAME = "computeInitialPrice";
	
	private final String buyOrderPriceProperty;
	
	public ComputeInitialPrice(@ConfigProperty(name = BUY_ORDER_LIMIT_PRICE_PROP_NAME) final String buyOrderPriceProperty) {
		this.buyOrderPriceProperty = buyOrderPriceProperty;
	}
	
	public BigDecimal execute(String symbol,
			OrderBookResponse orderBook) {
		if (buyOrderPriceProperty.startsWith("slippage")) {
			String slippage = extractValue(buyOrderPriceProperty);
	
			Map<BigDecimal, BigDecimal> asks = orderBook.asks();
			BigDecimal priceSum = BigDecimal.ZERO;
			BigDecimal volumeSum = BigDecimal.ZERO;
	
			if (!asks.isEmpty()) {
				BigDecimal prevPrice = null;
				
				for (Entry<BigDecimal, BigDecimal> ask: asks.entrySet()) {
					BigDecimal price = ask.getKey();
					BigDecimal volume = ask.getValue();
					
					if (prevPrice != null && getPercentDiff(prevPrice, price).compareTo(new BigDecimal(10)) < 0) {
						break;
					} else {
						priceSum = priceSum.add(price.multiply(volume));
						volumeSum = volumeSum.add(volume);
						prevPrice = price;
					}
				}
				
				BigDecimal slip = new BigDecimal(slippage)
						.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
				return priceSum.divide(volumeSum, 10, RoundingMode.HALF_UP).multiply(slip);
			} else {
				return BigDecimal.ZERO;
			}
		} else if (buyOrderPriceProperty.startsWith("manual")) {
			return new BigDecimal(extractValue(buyOrderPriceProperty));
		} else {
			if (!buyOrderPriceProperty.equalsIgnoreCase("auto")) {
				Log.errorf("The property '%s' has invalid value '%s'. Defaulting to 'auto'",
						BUY_ORDER_LIMIT_PRICE_PROP_NAME, buyOrderPriceProperty);
			}
			// TODO auto-computation
			return BigDecimal.ZERO;
		}
	}

	private String extractValue(String property) {
		return property.substring(property.indexOf(":") + 1);
	}

	private BigDecimal getPercentDiff(BigDecimal number, BigDecimal nextNumber) {
		return nextNumber.subtract(number).divide(number, 10, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
	}
}
