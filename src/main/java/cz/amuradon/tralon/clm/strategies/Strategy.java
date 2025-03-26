package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.clm.OrderBook;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

public interface Strategy {

	void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide);
	
	void computeInitialPrices(OrderBook orderBook);
	
	void onBaseBalanceUpdate(BigDecimal balance);
	
	void onQuoteBalanceUpdate(BigDecimal balance);
}
