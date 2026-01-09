package cz.amuradon.tralon.agent.connector.bybit.futures;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "bybitFutures-api")
@Retry(maxRetries = 3)
public interface BybitFuturesClient {

	@Path("/market/tickers")
	@GET
	@ClientQueryParam(name = "category", value = "linear")
	BybitTickersResponse tickers();
}
