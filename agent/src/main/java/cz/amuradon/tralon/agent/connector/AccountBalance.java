package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

public interface AccountBalance {
	String asset();
	BigDecimal available();
}
