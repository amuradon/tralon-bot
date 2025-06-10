package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.Trade;

public class TrailingProfitStopUpdatesProcessor extends AbstractUpdatesProcessor implements UpdatesProcessor {

	private BigDecimal maxPrice = BigDecimal.ZERO;
	private BigDecimal stopPrice = BigDecimal.ZERO;
	
    public TrailingProfitStopUpdatesProcessor(
    		final RestClient restClient,
    		final String symbol,
    		final int trailingStopBelow,
			final int trailingStopDelayMs,
			final int initialBuyOrderDelayMs) {
    	super(restClient, symbol, trailingStopBelow, trailingStopDelayMs, initialBuyOrderDelayMs);
    	
    }
    
	@Override
	public void processTradeUpdate(Trade trade) {
		BigDecimal price = trade.price();
		if (price.compareTo(maxPrice) > 0) {
			maxPrice = trade.price();
			stopPrice = maxPrice.multiply(new BigDecimal(100 - stopLossPc))
					.divide(new BigDecimal(100), symbolInfo.priceScale(), RoundingMode.DOWN);
		}

		if (positionOpened) {
			
			// TODO blocking I/O should be in different thread
			// Caution: market order does not work in first (one?) minute, it is immediately cancelled
			if (price.compareTo(stopPrice) <= 0) {
				if (trade.timestamp() - lastStopPriceDrop > stopLossDelayMs) {
					restClient.newOrder()
						.symbol(symbol)
						.side(Side.SELL)
						.type(OrderType.LIMIT)
						.size(baseQuantity)
						// Emulate market order to set lowest possible limit price
						.price(BigDecimal.ONE.scaleByPowerOfTen(-symbolInfo.priceScale()))
						.send();
					positionOpened = false;
				} else {
					lastStopPriceDrop = trade.timestamp();
				}
			} else {
				lastStopPriceDrop = Long.MAX_VALUE;
			}
			
			// If there is still at least partial of initial buy order
			if (initialBuyValid) {
				if (trade.price().compareTo(initialBuyPrice) > 0) {
					buyOrderPriceOverTimestamp = Math.min(buyOrderPriceOverTimestamp, trade.timestamp());
					if (System.currentTimeMillis() - buyOrderPriceOverTimestamp > initialBuyOrderDelayMs) {
						restClient.cancelOrder(buyOrderId, symbol);
						initialBuyValid = false;
					}
				} else {
					buyOrderPriceOverTimestamp = Long.MAX_VALUE;
				}
			}
		}
	}
	
}
