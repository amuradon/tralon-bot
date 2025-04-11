package cz.amuradon.tralon.agent.strategies.marketmaking;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.agent.Side;

public interface SpreadStrategy {

	BigDecimal calculate(Side side, Map<BigDecimal, BigDecimal> aggregatedOrders);

	String describe();
}
