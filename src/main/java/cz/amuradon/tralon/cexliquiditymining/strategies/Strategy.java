package cz.amuradon.tralon.cexliquiditymining.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.cexliquiditymining.OrderBook;
import cz.amuradon.tralon.cexliquiditymining.OrderBookUpdate;

public interface Strategy {

	void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide);
	
	void computeInitialPrices(OrderBook orderBook);
}
