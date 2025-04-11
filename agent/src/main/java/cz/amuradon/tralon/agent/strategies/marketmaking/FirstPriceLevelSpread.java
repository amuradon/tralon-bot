package cz.amuradon.tralon.agent.strategies.marketmaking;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.agent.Side;

public class FirstPriceLevelSpread implements SpreadStrategy {

	@Override
	public BigDecimal calculate(Side side, Map<BigDecimal, BigDecimal> aggregatedOrders) {
		return aggregatedOrders.entrySet().iterator().next().getKey();
	}

	@Override
	public String describe() {
		return getClass().getSimpleName();
	}

}
