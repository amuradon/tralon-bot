package cz.amuradon.tralon.cexliquidityminer;


import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;

import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeanConfig {

	private final KucoinClientBuilder kucoinClientBuilder;
	
	private final KucoinStrategyFactory strategyFactory;
	
	@Inject
	public BeanConfig(final KucoinStrategyFactory strategyFactory) {
		this.strategyFactory = strategyFactory;
		kucoinClientBuilder = new KucoinClientBuilder().withBaseUrl("https://openapi-v2.kucoin.com")
                .withApiKey("679ca3116425d800012adbc2", "94149a7a-69c8-4647-95b0-a83fc36933e5", "K1986dub27");
	}
	
	@ApplicationScoped
    @Produces
    public KucoinRestClient kucoinRestClient() {
    	return kucoinClientBuilder.buildRestClient();
    }
    
    @ApplicationScoped
    @Produces
    public KucoinPrivateWSClient kucoinPrivateWSClient() {
    	try {
			return kucoinClientBuilder.buildPrivateWSClient();
		} catch (IOException e) {
			throw new IllegalStateException("Could not build private WS client", e);
		}
    }
    
    @ApplicationScoped
    @Produces
    public KucoinPublicWSClient kucoinPublicWSClient() {
    	try {
			return kucoinClientBuilder.buildPublicWSClient();
		} catch (IOException e) {
			throw new IllegalStateException("Could not build public WS client", e);
		}
    }
    
    @ApplicationScoped
    @Produces
    public Map<String, Order> orders() {
    	return new ConcurrentHashMap<>();
    }
    
    @ApplicationScoped
    @Produces
    public Map<Side, PriceProposal> proposals() {
    	ConcurrentHashMap<Side, PriceProposal> proposals = new ConcurrentHashMap<>();
		proposals.put(Side.BUY, new PriceProposal());
		proposals.put(Side.SELL, new PriceProposal());
		return proposals;
    }

    public void onCamelContextStarted(@Observes CamelContextStartedEvent event) {
        strategyFactory.create().run();
    }
}
