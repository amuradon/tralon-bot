package cz.amuradon.tralon.agent.strategies.marketmaking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.BiFunction;

import cz.amuradon.tralon.agent.Side;

public class PercentFromBestSpread implements SpreadStrategy {

	private final BigDecimal percent;
	
    public PercentFromBestSpread(final BigDecimal percent) {
    	this.percent = percent;
	}
	
    @Override
    public BigDecimal calculate(Side side, Map<BigDecimal, BigDecimal> aggregatedOrders) {
    	BiFunction<BigDecimal, BigDecimal, BigDecimal> operation =
    			side == Side.BUY ? BigDecimal::subtract : BigDecimal::add;
    	BigDecimal best = aggregatedOrders.entrySet().iterator().next().getKey();
    	return best.multiply(operation.apply(BigDecimal.ONE, percent.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP)));
    }

	@Override
	public String describe() {
		return String.format("%s - %s", getClass().getSimpleName(), percent);
	}

}
