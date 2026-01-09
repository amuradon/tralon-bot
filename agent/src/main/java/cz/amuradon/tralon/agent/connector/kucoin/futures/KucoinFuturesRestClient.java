package cz.amuradon.tralon.agent.connector.kucoin.futures;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@KucoinFutures
@RestClientFactory // Required for proper usage with Instance
public class KucoinFuturesRestClient implements PerpetualFundingRateRestClient {

	private final KucoinFuturesClient client;
	
	@Inject
	public KucoinFuturesRestClient(@RestClient final KucoinFuturesClient client) {
		this.client = client;
	}
	
	@Override
	public List<FundingRate> fundingRates() {
		return client.activeContracts().data().stream()
				.filter(r -> "FFWCSX".equalsIgnoreCase(r.type()))
				.map(r -> new FundingRate(r.symbol().substring(0, r.symbol().length() - 1), r.fundingFeeRate(), r.nextFundingRateDateTime()))
				.collect(Collectors.toList());
	}

	@Override
	public String exchangeName() {
		return "Kucoin";
	}

}
