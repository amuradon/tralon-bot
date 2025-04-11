package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;

public interface AccountBalance {
	String asset();
	BigDecimal available();
}
