package cz.amuradon.tralon.clm.connector.binance;


import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.SpotClientImpl;

import cz.amuradon.tralon.clm.BeanConfig;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@ApplicationScoped
@IfBuildProfile("binance")
public class BinanceBeanConfig {

	@ApplicationScoped
    @Produces
    public SpotClient spotClient(@ConfigProperty(name = "binance.apiKey") String apiKey,
			@ConfigProperty(name = "binance.secretKey") String secretKey) {
    	return new SpotClientImpl(apiKey, secretKey);
    }
	
	@Singleton
    @Produces
    @Named(BeanConfig.SYMBOL)
    public String symbol(@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken) {
    	return baseToken + quoteToken;
    }
}
