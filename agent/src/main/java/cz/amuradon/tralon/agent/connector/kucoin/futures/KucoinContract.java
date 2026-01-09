package cz.amuradon.tralon.agent.connector.kucoin.futures;

import java.math.BigDecimal;

public record KucoinContract(String symbol, BigDecimal fundingFeeRate, long nextFundingRateDateTime, String type) {

}
