package cz.amuradon.tralon.agent.connector.bybit.futures;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@BybitFutures
@RestClientFactory // Required for proper usage with Instance
public class BybitFuturesRestClient implements PerpetualFundingRateRestClient {

	private final BybitFuturesClient client;
	
	@Inject
	public BybitFuturesRestClient(@RestClient final BybitFuturesClient client) {
		this.client = client;
	}
	
	@Override
	public List<FundingRate> fundingRates() {
		return client.tickers().result().list().stream()
				.map(r -> new FundingRate(r.symbol(), r.fundingRate(), r.nextFundingTime()))
				.collect(Collectors.toList());
	}

	@Override
	public String exchangeName() {
		return "Bybit";
	}

}
