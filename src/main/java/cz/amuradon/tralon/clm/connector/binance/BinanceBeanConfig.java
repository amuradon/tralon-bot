package cz.amuradon.tralon.clm.connector.binance;


import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.SpotClientImpl;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
@IfBuildProfile("binance")
public class BinanceBeanConfig {

	@ApplicationScoped
    @Produces
    public SpotClient spotClient(@ConfigProperty(name = "binance.apiKey") String apiKey,
			@ConfigProperty(name = "binance.secretKey") String secretKey) {
    	return new SpotClientImpl(apiKey, secretKey);
    }
}
