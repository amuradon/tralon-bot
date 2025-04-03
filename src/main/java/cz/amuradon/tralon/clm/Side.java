package cz.amuradon.tralon.clm;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Side {
	BUY(1),
	SELL(-1);

	private final int compare;
	
	private Side(final int compare) {
		this.compare = compare;
	}
	
	public static Side getValue(String side) {
		return valueOf(side.toUpperCase());
	}

	public boolean isPriceOutOfRange(BigDecimal updatePrice, BigDecimal currentPrice) {
		return currentPrice.compareTo(updatePrice) == compare && !(currentPrice.compareTo(BigDecimal.ZERO) == 0);
	}
	
	@JsonValue
	public int value() {
		return ordinal() + 1;
	}
}
