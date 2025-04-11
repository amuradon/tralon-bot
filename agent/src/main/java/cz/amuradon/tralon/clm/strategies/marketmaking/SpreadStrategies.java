package cz.amuradon.tralon.clm.strategies.marketmaking;

import java.math.BigDecimal;
import java.util.function.Function;

public enum SpreadStrategies {

	FIRST_PRICE_LEVEL(v -> new FirstPriceLevelSpread(), "First price level", "NOT USED"),
	AFTER_VOLUME(AfterVolumeSpread::new, "After volume", "Quote volume to place order behind"),
	PERCENT_FROM_BEST(PercentFromBestSpread::new, "Spread percentage", "Spread percentage");
	
	private final Function<BigDecimal, SpreadStrategy> create;
	
	private final String displayName;

	private final String valueCaption;
	
	private SpreadStrategies(final Function<BigDecimal, SpreadStrategy> create, final String displayName,
			final String valueCaption) {
		this.create = create;
		this.displayName = displayName;
		this.valueCaption = valueCaption;
	}
	
	public SpreadStrategy create(BigDecimal value) {
		return create.apply(value);
	}
	
	public String displayName() {
		return displayName;
	}
	
	public String valueCaption() {
		return valueCaption;
	}
	
	public static SpreadStrategies fromDisplayName(String displayName) {
		for (SpreadStrategies exchange : values()) {
			if (displayName.equals(exchange.displayName)) {
				return exchange;
			}
		}
		throw new IllegalArgumentException("Could not find spread strategy " + displayName);
	}
}
