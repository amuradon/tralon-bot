package cz.amuradon.tralon.agent.strategies;

import java.math.BigDecimal;

public record SymbolValues(BigDecimal lastPrice,
		BigDecimal quoteVolume) {

}
