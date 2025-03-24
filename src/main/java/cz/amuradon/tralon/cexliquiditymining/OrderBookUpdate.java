package cz.amuradon.tralon.cexliquiditymining;

import java.math.BigDecimal;

public record OrderBookUpdate(long sequence,
		BigDecimal price,
		BigDecimal size,
		Side side,
		long time) {

}
