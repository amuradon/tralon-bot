package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.clm.Order;
import cz.amuradon.tralon.clm.PriceProposal;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.RestClient;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

// @Singleton required due to abstract parent class
@Singleton
@Named("WallBeforeStrategy")
public class WallBeforeStrategy extends AbstractStrategy {

	private final BigDecimal sideVolumeThreshold;
	
    @Inject
    public WallBeforeStrategy(
    		@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
    		@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final RestClient restClient,
    		@ConfigProperty(name = "baseToken") final String baseToken,
    		@ConfigProperty(name = "quoteToken") final String quoteToken,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final Map<String, Order> orders,
    		final ScheduledExecutorService scheduler) {
    	super(priceChangeDelayMs, priceProposals, restClient, baseToken, quoteToken,
    			maxBalanceToUse, orders, scheduler);
    	this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
	}
	
    BigDecimal getTargetPriceLevel(Map<BigDecimal, BigDecimal> aggregatedOrders) {
		BigDecimal volume = BigDecimal.ZERO;
    	BigDecimal price = BigDecimal.ZERO;
    	for (Entry<BigDecimal, BigDecimal> entry : aggregatedOrders.entrySet()) {
    		price = entry.getKey();
			volume = volume.add(price.multiply(entry.getValue()));
			if (volume.compareTo(sideVolumeThreshold) >= 0) {
				break;
			}
		}
    	
    	return price;
    }
}
