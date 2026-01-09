package cz.amuradon.tralon.agent.connector.mexc.futures;

import java.math.BigDecimal;

public record MexcFundingRate(String symbol, BigDecimal fundingRate, long nextSettleTime) {

}
