package cz.amuradon.tralon.master.web;

import java.math.BigDecimal;

public record Trade(

	long timestamp,
    String exchange,
    String symbol,
    String side,
    BigDecimal avgPrice,
    BigDecimal quantity,
    BigDecimal quoteQuantity,
    BigDecimal commission
	) {
}
