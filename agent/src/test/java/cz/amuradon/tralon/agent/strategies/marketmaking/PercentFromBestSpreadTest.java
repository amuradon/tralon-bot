package cz.amuradon.tralon.agent.strategies.marketmaking;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.strategies.marketmaking.PercentFromBestSpread;

public class PercentFromBestSpreadTest {

	@Test
	public void calculatBuy() {
		PercentFromBestSpread calculator = new PercentFromBestSpread(new BigDecimal("0.5"));
		BigDecimal result = calculator.calculate(Side.BUY, Map.of(new BigDecimal("84594"), new BigDecimal("1000000")));
		Assertions.assertEquals(new BigDecimal("84171.030000"), result);
	}

	@Test
	public void calculatSell() {
		PercentFromBestSpread calculator = new PercentFromBestSpread(new BigDecimal("0.5"));
		BigDecimal result = calculator.calculate(Side.SELL, Map.of(new BigDecimal("84594"), new BigDecimal("1000000")));
		Assertions.assertEquals(new BigDecimal("85016.970000"), result);
	}
}
