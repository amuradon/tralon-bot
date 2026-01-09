package cz.amuradon.tralon.agent.connector.mexc.futures;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "mexcFutures-api")
@Retry(maxRetries = 3)
public interface MexcFuturesClient {

	@Path("/funding_rate")
	@GET
	MexcFundingRatesResponse fundingRates();
}
