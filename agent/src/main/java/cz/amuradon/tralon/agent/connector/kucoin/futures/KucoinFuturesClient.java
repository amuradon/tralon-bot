package cz.amuradon.tralon.agent.connector.kucoin.futures;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "kucoinFutures-api")
@Retry(maxRetries = 3)
public interface KucoinFuturesClient {

	@Path("/active")
	@GET
	KucoinActiveContractsResponse activeContracts();
}
