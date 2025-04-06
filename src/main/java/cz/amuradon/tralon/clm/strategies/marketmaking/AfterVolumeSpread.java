package cz.amuradon.tralon.clm.strategies.marketmaking;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;

import cz.amuradon.tralon.clm.Side;

public class AfterVolumeSpread implements SpreadCalculator {

	private final BigDecimal volumeThreshold;
	
    public AfterVolumeSpread(final int volumeThreshold) {
    	this.volumeThreshold = new BigDecimal(volumeThreshold);
	}
	
    @Override
    public BigDecimal calculate(Side side, Map<BigDecimal, BigDecimal> aggregatedOrders) {
		BigDecimal volume = BigDecimal.ZERO;
    	BigDecimal price = BigDecimal.ZERO;
    	for (Entry<BigDecimal, BigDecimal> entry : aggregatedOrders.entrySet()) {
    		price = entry.getKey();
			volume = volume.add(price.multiply(entry.getValue()));
			if (volume.compareTo(volumeThreshold) >= 0) {
				break;
			}
		}
    	
    	return price;
    }

	@Override
	public String describe() {
		return String.format("%s - %s", getClass().getSimpleName(), volumeThreshold);
	}

}
