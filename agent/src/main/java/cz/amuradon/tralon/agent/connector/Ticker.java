package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

public interface Ticker {
		
	String symbol();
	BigDecimal lastPrice();
	BigDecimal quoteVolume();
	
}
