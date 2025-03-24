package cz.amuradon.tralon.cexliquiditymining.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.cexliquiditymining.BalanceHolder;
import cz.amuradon.tralon.cexliquiditymining.OrderBook;
import cz.amuradon.tralon.cexliquiditymining.OrderBookUpdate;

public class MarketMakingStrategy implements Strategy {

	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void computeInitialPrices(OrderBook orderBook) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onBalanceUpdate(BalanceHolder balanceHolder) {
		// TODO Auto-generated method stub
		
	}

}
