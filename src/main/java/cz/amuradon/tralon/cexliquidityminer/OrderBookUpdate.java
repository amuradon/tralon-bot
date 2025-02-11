package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;

public record OrderBookUpdate(long sequence,
		BigDecimal price,
		BigDecimal size,
		Side side) {

}
