package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonFormat(shape = Shape.ARRAY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Kline(
		long openTime,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		BigDecimal volume,
		long closeTime,
		BigDecimal quoteVolume,
		int numberOfTrades,
		BigDecimal takerVolume
) {
}
