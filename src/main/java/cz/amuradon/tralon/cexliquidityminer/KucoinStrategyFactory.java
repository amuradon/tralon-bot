package cz.amuradon.tralon.cexliquidityminer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Startup
@ApplicationScoped
public class KucoinStrategyFactory {

	private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final int orderBookQuoteVolumeBefore;
	
	private final int maxQuoteBalanceToUse;
	
	private final int priceChangeDelayMs;
	
	@Inject
    public KucoinStrategyFactory(final KucoinRestClient restClient,
    		final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate,
    		@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken,
    		@ConfigProperty(name = "orderBookQuoteVolumeBefore") int orderBookQuoteVolumeBefore,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") int maxQuoteBalanceToUse,
    		@ConfigProperty(name = "priceChangeDelayMs") int priceChangeDelayMs
    ) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		this.orderBookQuoteVolumeBefore = orderBookQuoteVolumeBefore;
		this.maxQuoteBalanceToUse = maxQuoteBalanceToUse;
		this.priceChangeDelayMs = priceChangeDelayMs;
    }
	
	@PostConstruct
	public void init() {
		new KucoinStrategy(restClient, wsClientPublic, wsClientPrivate, baseToken, quoteToken,
				orderBookQuoteVolumeBefore, maxQuoteBalanceToUse, priceChangeDelayMs).run();
	}
}
