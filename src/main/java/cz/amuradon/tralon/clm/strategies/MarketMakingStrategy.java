package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;
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

// TODO create on-demand
//@Singleton required due to abstract parent class
//@Singleton
public class MarketMakingStrategy extends AbstractStrategy {

//    @Inject
    public MarketMakingStrategy (
    		@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final RestClient restClient,
    		final String symbol,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final Map<String, Order> orders,
    		final ScheduledExecutorService scheduler,
    		final WebsocketClient websocketClient,
    		final String baseToken,
    		final String quoteToken,
    		final OrderBookManager orderBookManager) {
    	super(priceChangeDelayMs, priceProposals, restClient, symbol,
    			maxBalanceToUse, orders, scheduler, websocketClient, baseToken, quoteToken, orderBookManager);
	}

    /*
     * TODO
     * Asymtericy spread - ask price vzdy nejnizsi, bid dale od nejvyssi ceny
     * Kontrolovat, že spread mezi ask a bid je vetsi nez fees
     * Kontrolovat, ze neprodavam drazsi nez jsem koupil - ale v pripade velkeho padu ceny ano
     * Trade updates
     * Pri velke volatilite dynamicky spread
     * Pri velkem padu ujizdet s bid, pri vzrustu mirne ujizdet 
     */
    
	@Override
	BigDecimal getTargetPriceLevel(Map<BigDecimal, BigDecimal> aggregatedOrders) {
		return aggregatedOrders.entrySet().iterator().next().getKey();
	}

	@Override
	public String getDescription() {
		// TODO complete description
		return getClass().getSimpleName();
	}
}
