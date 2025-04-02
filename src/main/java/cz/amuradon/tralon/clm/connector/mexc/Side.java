package cz.amuradon.tralon.clm.connector.mexc;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Side {

	BUY,
	SELL;
	
	@JsonValue
	public int value() {
		return ordinal() + 1;
	}
}
