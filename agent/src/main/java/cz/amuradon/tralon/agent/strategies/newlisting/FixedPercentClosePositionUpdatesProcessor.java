package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.Trade;

public class FixedPercentClosePositionUpdatesProcessor extends AbstractUpdatesProcessor implements UpdatesProcessor {

	private final int takeProfitPc;
	
	private BigDecimal takeProfitPrice;
	private BigDecimal stopLossPrice;
	
	private String sellOrderId;
	
    public FixedPercentClosePositionUpdatesProcessor(
    		final RestClient restClient,
    		final String symbol, int takeProfitPc, int stopLossPc, int stopLossDelayMs, int initialBuyOrderValidityMs) {
    	super(restClient, symbol, stopLossPc, stopLossDelayMs, initialBuyOrderValidityMs);
    	this.takeProfitPc = takeProfitPc;
    }
    
	@Override
	public void processTradeUpdate(Trade trade) {

		// XXX synchronization ?
		BigDecimal price = trade.price();

		if (positionOpened) {
			
			// TODO blocking I/O should be in different thread
			// Caution: market order does not work in first (one?) minute, it is immediately cancelled
			if (price.compareTo(stopLossPrice) <= 0) {
				if (trade.timestamp() - lastStopPriceDrop > stopLossDelayMs) {
					if (sellOrderId != null) {
						restClient.cancelOrder(sellOrderId, symbol);
					}
					
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
	
	@Override
	void initialBuyOrderDone() {
		if (baseQuantity != null) {
			stopLossPrice = actualBuyPrice.multiply(new BigDecimal(100 - stopLossPc))
					.divide(new BigDecimal(100), symbolInfo.priceScale(), RoundingMode.DOWN);
	    	takeProfitPrice = actualBuyPrice.multiply(new BigDecimal(100 + takeProfitPc))
	    			.divide(new BigDecimal(100), symbolInfo.priceScale(), RoundingMode.DOWN);
			sellOrderId = restClient.newOrder()
				.symbol(symbol)
				.side(Side.SELL)
				.type(OrderType.LIMIT)
				.size(baseQuantity)
				.price(takeProfitPrice)
				.send();
		}
	}
}
