package cz.amuradon.tralon.cexliquiditymining;


import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import cz.amuradon.tralon.cexliquiditymining.strategies.Strategy;
import cz.amuradon.tralon.cexliquiditymining.strategies.WallBeforeStrategy;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeanConfig {

	public static final String STRATEGY = "MarketMakingStrategy";
	
	private final KucoinClientBuilder kucoinClientBuilder;
	
	private final KucoinEngineFactory strategyFactory;
	
	@Inject
	public BeanConfig(final KucoinEngineFactory strategyFactory) {
		this.strategyFactory = strategyFactory;
		kucoinClientBuilder = new KucoinClientBuilder().withBaseUrl("https://openapi-v2.kucoin.com")
                .withApiKey("67e12bcf6fb8e00001f0cda5", "84b9d7b5-4bcc-46dc-a549-bc2677e674ea", "K1986dub27");
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
    
    // XXX Pravdepodobne ne tak ciste reseni, asi bych mel delegovat do vlastniho vlakna?
    @Startup
    public void start() {
    	new Thread(strategyFactory.create(), "Startup").start();
    }
}
