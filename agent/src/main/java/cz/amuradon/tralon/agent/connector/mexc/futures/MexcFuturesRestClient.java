package cz.amuradon.tralon.agent.connector.mexc.futures;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@MexcFutures
@RestClientFactory // Required for proper usage with Instance
public class MexcFuturesRestClient implements PerpetualFundingRateRestClient {

	private final MexcFuturesClient client;
	
	@Inject
	public MexcFuturesRestClient(@RestClient final MexcFuturesClient client) {
		this.client = client;
	}
	
	@Override
	public List<FundingRate> fundingRates() {
		return client.fundingRates().data().stream()
				.map(r -> new FundingRate(r.symbol().replace("_", ""), r.fundingRate(), r.nextSettleTime()))
				.collect(Collectors.toList());
	}

	@Override
	public String exchangeName() {
		return "MEXC";
	}

}
