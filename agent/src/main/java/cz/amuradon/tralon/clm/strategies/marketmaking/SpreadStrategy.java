package cz.amuradon.tralon.clm.strategies.marketmaking;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.clm.Side;

public interface SpreadStrategy {

	BigDecimal calculate(Side side, Map<BigDecimal, BigDecimal> aggregatedOrders);

	String describe();
}
