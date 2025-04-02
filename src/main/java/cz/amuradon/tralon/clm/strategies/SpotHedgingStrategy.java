package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.clm.OrderBook;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;

public class SpotHedgingStrategy implements Strategy {

	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		// TODO Auto-generated method stub

	}

	@Override
	public void computeInitialPrices(OrderBook orderBook) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBaseBalanceUpdate(BigDecimal balance) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onQuoteBalanceUpdate(BigDecimal balance) {
		// TODO Auto-generated method stub

	}

}
