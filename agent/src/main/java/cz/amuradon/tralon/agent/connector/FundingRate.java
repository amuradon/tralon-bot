package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

public record FundingRate(String symbol, BigDecimal lastFundingRate, long nextFundingTime) {

}
