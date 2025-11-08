package cz.amuradon.tralon.agent.connector.binance.alpha;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "binanceAlpha-api")
@Retry(maxRetries = 10)
public interface BinanceAlphaClient {

	// Token list has necessary information, 24h ticker is only allowed per symbol
	@Path("/wallet-direct/buw/wallet/cex/alpha/all/token/list")
	@GET
	TokenList ticker();

}
