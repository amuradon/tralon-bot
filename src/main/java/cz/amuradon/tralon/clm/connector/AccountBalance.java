package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;

public interface AccountBalance {
	String token();
	BigDecimal available();
}
