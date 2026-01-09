package cz.amuradon.tralon.agent.connector.gate.futures;

import java.util.List;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "gateFutures-api")
@Retry(maxRetries = 3)
public interface GateFuturesClient {

	@Path("/futures/usdt/contracts")
	@GET
	List<GateContract> contracts();
}
