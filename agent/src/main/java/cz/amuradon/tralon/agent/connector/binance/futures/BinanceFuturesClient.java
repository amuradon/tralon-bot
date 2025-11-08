package cz.amuradon.tralon.agent.connector.binance.futures;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import cz.amuradon.tralon.agent.connector.binance.BinanceTicker;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "binanceFutures-api")
@Retry(maxRetries = 10)
public interface BinanceFuturesClient {

	@Path("/fapi/v1/ticker/24hr")
	@GET
	BinanceTicker[] ticker();

}
