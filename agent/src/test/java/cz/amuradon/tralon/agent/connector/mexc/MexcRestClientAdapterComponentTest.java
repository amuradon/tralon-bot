package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MexcExchangeMock.class)
public class MexcRestClientAdapterComponentTest {

	@Mexc
	@RestClientFactory
	@Inject
	MexcRestClientAdapter client;
	
	@Test
	public void testOrderBook() {
		OrderBookResponse orderResponse = client.orderBook("TKNUSDT");

		Assertions.assertEquals(10050L, orderResponse.sequence());
		Assertions.assertEquals(3, orderResponse.asks().size());
		Assertions.assertEquals(3, orderResponse.bids().size());
		
		Assertions.assertEquals(new BigDecimal("8.88"), orderResponse.asks().get(new BigDecimal("0.3200")));
		Assertions.assertEquals(new BigDecimal("4.44"), orderResponse.bids().get(new BigDecimal("0.2800")));
		
	}
	
}
