package cz.amuradon.tralon.cexliquiditymining;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import cz.amuradon.tralon.cexliquiditymining.strategies.Strategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class KucoinEngineFactory {

	private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final Map<String, Order> orders;
	
	private final OrderBookManager orderBookManager;
	
	private final Strategy strategy;
	
	@Inject
    public KucoinEngineFactory(final KucoinRestClient restClient,
    		final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate,
    		@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken,
    		final Map<String, Order> orders,
    		final OrderBookManager orderBookManager,
    		final Strategy strategy) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		this.orders = orders;
		this.orderBookManager = orderBookManager;
		this.strategy = strategy;
    }
	
	public Runnable create() {
		return new KucoinEngine(restClient, wsClientPublic, wsClientPrivate, baseToken, quoteToken,
				orders, orderBookManager, strategy);
	}
}
