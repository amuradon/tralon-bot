package cz.amuradon.tralon.cexliquidityminer;


import java.io.IOException;

import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeanConfig {

	private final KucoinClientBuilder kucoinClientBuilder;
	
	@Inject
	public BeanConfig() {
		kucoinClientBuilder = new KucoinClientBuilder().withBaseUrl("https://openapi-v2.kucoin.com")
                .withApiKey("679ca3116425d800012adbc2", "94149a7a-69c8-4647-95b0-a83fc36933e5", "K1986dub27");
	}
	
    @Produces
    public KucoinRestClient kucoinRestClient() {
    	return kucoinClientBuilder.buildRestClient();
    }
    
    @Produces
    public KucoinPrivateWSClient kucoinPrivateWSClient() {
    	try {
			return kucoinClientBuilder.buildPrivateWSClient();
		} catch (IOException e) {
			throw new IllegalStateException("Could not build private WS client", e);
		}
    }
    
    @Produces
    public KucoinPublicWSClient kucoinPublicWSClient() {
    	try {
			return kucoinClientBuilder.buildPublicWSClient();
		} catch (IOException e) {
			throw new IllegalStateException("Could not build public WS client", e);
		}
    }
}
