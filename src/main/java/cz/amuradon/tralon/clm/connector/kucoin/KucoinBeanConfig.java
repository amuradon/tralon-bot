package cz.amuradon.tralon.clm.connector.kucoin;


import java.io.IOException;

import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProfile("kucoin")
public class KucoinBeanConfig {

	private final KucoinClientBuilder kucoinClientBuilder;
	
	@Inject
	public KucoinBeanConfig() {
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
    
}
