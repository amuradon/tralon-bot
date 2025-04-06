package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

public interface Strategy {
	
	void start();

	void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide);
	
	void onBaseBalanceUpdate(BigDecimal balance);
	
	void onQuoteBalanceUpdate(BigDecimal balance);

	void stop();
	
	String getDescription();
}
