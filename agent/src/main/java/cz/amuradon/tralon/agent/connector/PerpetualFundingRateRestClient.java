package cz.amuradon.tralon.agent.connector;

import java.util.List;

public interface PerpetualFundingRateRestClient {

	List<FundingRate> fundingRates();

	String exchangeName();
}
