package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.clm.BeanConfig;
import cz.amuradon.tralon.clm.OrderBookManager;
import cz.amuradon.tralon.clm.PriceProposal;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.WebsocketClient;
import cz.amuradon.tralon.clm.model.Order;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

// @Singleton required due to abstract parent class
//@Singleton
public class WallBeforeStrategy extends AbstractStrategy {

	private final BigDecimal sideVolumeThreshold;
	
//    @Inject
    public WallBeforeStrategy(
    		@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
    		@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final RestClient restClient,
    		@Named(BeanConfig.SYMBOL) final String symbol,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final Map<String, Order> orders,
    		final ScheduledExecutorService scheduler,
    		final WebsocketClient websocketClient,
    		final String baseToken,
    		final String quoteToken,
    		final OrderBookManager orderBookManager) {
    	super(priceChangeDelayMs, priceProposals, restClient, symbol,
    			maxBalanceToUse, orders, scheduler, websocketClient, baseToken, quoteToken, orderBookManager);
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

	@Override
	public String getDescription() {
		// TODO complete description
		return getClass().getSimpleName();
	}
}
