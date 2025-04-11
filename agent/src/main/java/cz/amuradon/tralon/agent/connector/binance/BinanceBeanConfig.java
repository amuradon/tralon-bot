package cz.amuradon.tralon.agent.connector.binance;


import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.SpotClientImpl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class BinanceBeanConfig {

	@ApplicationScoped
    @Produces
    public SpotClient spotClient(@ConfigProperty(name = "binance.apiKey") String apiKey,
			@ConfigProperty(name = "binance.secretKey") String secretKey) {
    	return new SpotClientImpl(apiKey, secretKey);
    }
	
}
