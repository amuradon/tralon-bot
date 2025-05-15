package cz.amuradon.tralon.agent.connector.mexc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Formatter.BigDecimalLayoutForm;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.DataStoringRestClientListener;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MexcExchangeMock.class)
public class MexcRestClientAdapterIntegrationTest {

	@Mexc
	@RestClientFactory
	@Inject
	MexcRestClientAdapter client;
	
	@Inject
	ExecutorService service;
	
	@Test
	public void testOrderBook() throws IOException {
		Path dirs = Path.of("C:\\work\\tralon\\test-data");
		Files.createDirectories(dirs);
		client.setListener(new DataStoringRestClientListener(service, dirs));
		OrderBookResponse orderResponse = client.orderBook("TKNUSDT");

		Assertions.assertEquals(10050L, orderResponse.sequence());
		Assertions.assertEquals(3, orderResponse.asks().size());
		Assertions.assertEquals(3, orderResponse.bids().size());
		
		Assertions.assertEquals(new BigDecimal("8.88"), orderResponse.asks().get(new BigDecimal("0.3200")));
		Assertions.assertEquals(new BigDecimal("4.44"), orderResponse.bids().get(new BigDecimal("0.2800")));
	}

	@Test
	public void testNewOrder() throws IOException {
		String orderId = client.newOrder().symbol("TKNUSDT").side(Side.BUY)
				.price(new BigDecimal("0.1")).size(BigDecimal.ONE).clientOrderId("TEST").send();
		System.out.println(orderId);
	}
	
}
