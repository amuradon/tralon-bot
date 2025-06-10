package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.SymbolInfo;
import io.quarkus.logging.Log;

abstract class AbstractUpdatesProcessor implements UpdatesProcessor {

	final RestClient restClient;
	
    final String symbol;
    
    final int stopLossPc;

	final int stopLossDelayMs;
	
	final int initialBuyOrderDelayMs;
    
	boolean initialBuyValid = true;
	
	boolean positionOpened = false;
	
	BigDecimal baseQuantity;
	
	String buyOrderId;
	
	String buyClientOrderId;
	
	SymbolInfo symbolInfo;
	
	BigDecimal initialBuyPrice;
	
	BigDecimal actualBuyPrice;
	
	long lastStopPriceDrop = Long.MAX_VALUE;
	
	long buyOrderPriceOverTimestamp = Long.MAX_VALUE;
	
    AbstractUpdatesProcessor(final RestClient restClient, final String symbol,
    		final int stopLossPc, final int stopLossDelayMs, final int initialBuyOrderDelayMs) {
		this.restClient = restClient;
    	this.symbol = symbol;
    	this.stopLossPc = stopLossPc;
		this.stopLossDelayMs = stopLossDelayMs;
		this.initialBuyOrderDelayMs = initialBuyOrderDelayMs;
    }
    
	@Override
	public void processOrderUpdate(OrderChange orderChange) {
		Log.infof("Order update: %s", orderChange);
		if (orderChange.clientOrderId().equalsIgnoreCase(buyClientOrderId)){
			if (orderChange.status() == OrderStatus.PARTIALLY_FILLED) {
				positionOpened = true;
				baseQuantity = orderChange.cumulativeQuantity();
				actualBuyPrice = orderChange.price();
			} else if (orderChange.status() == OrderStatus.FILLED) {
				positionOpened = true;
				initialBuyValid = false;
				baseQuantity = orderChange.cumulativeQuantity();
				// XXX average price
				actualBuyPrice = orderChange.price();
				initialBuyOrderDone();
			} else if (orderChange.status() == OrderStatus.CANCELED) {
				initialBuyValid = false;
				initialBuyOrderDone();
			}
		 }
	}
	
	void initialBuyOrderDone() {
		// Do nothing by default
	}

	@Override
	public void querySymbolInfo() {
		symbolInfo = restClient.cacheSymbolDetails(symbol);
	}

	@Override
	public void setClientOrderId(String clientOrderId) {
		this.buyClientOrderId = clientOrderId;
	}
	
	@Override
	public void setBuyOrderId(String orderId) {
		this.buyOrderId = orderId;
	}
	
	@Override
	public void setInitialBuyPrice(BigDecimal initialBuyPrice) {
		this.initialBuyPrice = initialBuyPrice; 
	}
}
