package cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding;

import java.util.List;

import cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding.PerpetualFundingArbitrageScanner.FundingRatesRow;

public record ChannelData(List<FundingRatesRow> sortedRows, List<String> sortedExchangeNames, long closestFundingMillis) {

	public static final String CHANNEL_NAME = "perpFundArbData";
}
