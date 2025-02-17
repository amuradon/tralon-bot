package cz.amuradon.tralon.cexliquidityminer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class SideTest {

	@Test
	public void isPriceOutOfRangeBuyZero() {
		assertFalse(Side.BUY.isPriceOutOfRange(new BigDecimal("1.5"), BigDecimal.ZERO));
	}

	@Test
	public void isPriceOutOfRangeBuyUpdateGreater() {
		assertFalse(Side.BUY.isPriceOutOfRange(new BigDecimal("1.7"), new BigDecimal("1.6")));
	}

	@Test
	public void isPriceOutOfRangeBuyUpdateLesser() {
		assertTrue(Side.BUY.isPriceOutOfRange(new BigDecimal("1.5"), new BigDecimal("1.6")));
	}

	@Test
	public void isPriceOutOfRangeSellZero() {
		assertFalse(Side.SELL.isPriceOutOfRange(new BigDecimal("1.5"), BigDecimal.ZERO));
	}
	
	@Test
	public void isPriceOutOfRangeSellUpdateGreater() {
		assertTrue(Side.SELL.isPriceOutOfRange(new BigDecimal("1.7"), new BigDecimal("1.6")));
	}
	
	@Test
	public void isPriceOutOfRangeSellUpdateLesser() {
		assertFalse(Side.SELL.isPriceOutOfRange(new BigDecimal("1.5"), new BigDecimal("1.6")));
	}
}
