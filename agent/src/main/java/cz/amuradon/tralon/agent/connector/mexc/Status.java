package cz.amuradon.tralon.agent.connector.mexc;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {

	NOT_TRADED,
	FULLY_TRADED,
	PARTIALLY_TRADED,
	CANCELLED,
	PARTIALLY_CANCELLED;
	
	@JsonValue
	public int value() {
		return ordinal() + 1;
	}
}
