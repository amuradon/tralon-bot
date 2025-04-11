package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;
import java.util.Map;

public interface OrderBookResponse {

	long sequence();

	Map<BigDecimal, BigDecimal> asks();

	Map<BigDecimal, BigDecimal> bids();

}
