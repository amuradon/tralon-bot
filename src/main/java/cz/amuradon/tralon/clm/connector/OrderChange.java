package cz.amuradon.tralon.clm.connector;

import java.math.BigDecimal;

public interface OrderChange {

	String getType();

	String getSymbol();

	String getOrderId();

	String getSide();

	BigDecimal getSize();

	BigDecimal getPrice();

	BigDecimal getRemainSize();

}
