package cz.amuradon.tralon.cexliquidityminer;

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
	
	@Inject
    public KucoinStrategyFactory(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
    }
	
	@PostConstruct
	public void init() {
		new KucoinStrategy(restClient, wsClientPublic, wsClientPrivate, "VERSE", "USDT", 1000, 100, 5000).run();
	}
}
