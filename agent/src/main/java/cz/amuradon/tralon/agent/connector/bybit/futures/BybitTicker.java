package cz.amuradon.tralon.agent.connector.bybit.futures;

import java.math.BigDecimal;

public record BybitTicker(String symbol, BigDecimal fundingRate, long nextFundingTime) {

}
