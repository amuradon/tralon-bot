package cz.amuradon.tralon.agent.connector.gate.futures;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@GateFutures
@RestClientFactory // Required for proper usage with Instance
public class GateFuturesRestClient implements PerpetualFundingRateRestClient {

	private final GateFuturesClient client;
	
	@Inject
	public GateFuturesRestClient(@RestClient final GateFuturesClient client) {
		this.client = client;
	}
	
	@Override
	public List<FundingRate> fundingRates() {
		return client.contracts().stream()
				.map(r -> new FundingRate(r.symbol().replace("_", ""), r.fundingFeeRate(), r.nextFundingRateDateTime() * 1000))
				.collect(Collectors.toList());
	}

	@Override
	public String exchangeName() {
		return "Gate";
	}

}
