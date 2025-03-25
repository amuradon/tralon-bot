package cz.amuradon.tralon.cexliquiditymining.strategies;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinRestClient;

import cz.amuradon.tralon.cexliquiditymining.Order;
import cz.amuradon.tralon.cexliquiditymining.PriceProposal;
import cz.amuradon.tralon.cexliquiditymining.Side;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

//@Singleton required due to abstract parent class
@Singleton
@Named("MarketMakingStrategy")
public class MarketMakingStrategy extends AbstractStrategy {

    @Inject
    public MarketMakingStrategy (
    		@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final KucoinRestClient restClient,
    		@ConfigProperty(name = "baseToken") final String baseToken,
    		@ConfigProperty(name = "quoteToken") final String quoteToken,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final Map<String, Order> orders) {
    	super(priceChangeDelayMs, priceProposals, restClient, baseToken, quoteToken, maxBalanceToUse, orders);
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

}
