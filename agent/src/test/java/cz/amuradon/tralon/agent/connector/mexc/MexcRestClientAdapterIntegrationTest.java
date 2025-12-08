package cz.amuradon.tralon.agent.connector.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.DataStoringRestClientListener;
import cz.amuradon.tralon.agent.connector.NoValidTradePriceException;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.RequestException;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.TradeDirectionNotAllowedException;
import io.fabric8.mockwebserver.MockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

// @QuarkusTest
// @QuarkusTestResource(MexcExchangeMock.class)
public class MexcRestClientAdapterIntegrationTest {

	@Mexc
	@RestClientFactory
	@Inject
	MexcRestClientAdapter client;
	
	@Inject
	ExecutorService service;
	
	@InjectHttpServerMock
	MockServer mockServer;
	
	// @Test
	public void testOrderBook() throws IOException {
		Path dirs = Path.of("C:\\work\\tralon\\test-data");
		Files.createDirectories(dirs);
		client.setListener(new DataStoringRestClientListener(service, dirs));
		OrderBookResponse orderResponse = client.orderBook("TKNUSDT");

		assertEquals(10050L, orderResponse.sequence());
		assertEquals(3, orderResponse.asks().size());
		assertEquals(3, orderResponse.bids().size());
		
		assertEquals(new BigDecimal("8.88"), orderResponse.asks().get(new BigDecimal("0.3200")));
		assertEquals(new BigDecimal("4.44"), orderResponse.bids().get(new BigDecimal("0.2800")));
	}

	// @Test
	public void newOrderShouldGetOrderId() throws Exception {
		String orderId = client.newOrder().symbol("TKNUSDT").side(Side.BUY).type(OrderType.LIMIT)
			.price(new BigDecimal("0.1")).size(BigDecimal.ONE).clientOrderId("ValidNewOrder")
			.timestamp(123456789L).recvWindow(6000).send();
		
		assertEquals("orderId", orderId);
		assertEquals(mockServer.getRequestCount(), 1, "The new order request should not retry automatically");
		assertEquals("/order?symbol=TKNUSDT&side=BUY&type=LIMIT&price=0.10&quantity=1.00&"
				+ "newClientOrderId=ValidNewOrder&timestamp=123456789&recvWindow=6000&"
				+ "signature=b950ffe712f5b141de67744a2dea8cdc9f3f031ec23eef44beb53ae1292bf354",
				mockServer.takeRequest(10, TimeUnit.SECONDS).getPath());
	}

	// @Test
	public void newOrderShouldNotRetryOnNoValidPrice() throws Exception {
		NoValidTradePriceException exception = Assertions.assertThrows(NoValidTradePriceException.class, () -> 
			client.newOrder().symbol("TKNUSDT").side(Side.BUY)
				.price(new BigDecimal("0.1")).size(BigDecimal.ONE).clientOrderId("NoValidTradePriceException").send());
		
		assertEquals(new BigDecimal("0.05"), exception.validPrice());
		assertEquals(mockServer.getRequestCount(), 1, "The new order request should not retry automatically");
	}

	// @Test
	public void newOrderShouldNotRetryOnTradeDirectionNotAllowed() throws Exception {
		assertThrows(TradeDirectionNotAllowedException.class, () -> 
			client.newOrder().symbol("TKNUSDT").side(Side.BUY)
				.price(new BigDecimal("0.1")).size(BigDecimal.ONE)
				.clientOrderId("TradeDirectionNotAllowedException").send());
		
		assertEquals(mockServer.getRequestCount(), 1, "The new order request should not retry automatically");
	}

	//@Test
	public void newOrderShouldNotRetryOnTooManyRequests() throws Exception {
		Assertions.assertThrows(RequestException.class, () -> 
			client.newOrder().symbol("TKNUSDT").side(Side.BUY)
				.price(new BigDecimal("0.1")).size(BigDecimal.ONE)
				.clientOrderId("TooManyRequestsError").send());
		
		assertEquals(mockServer.getRequestCount(), 1, "The new order request should not retry automatically");
	}
	
	@AfterEach
	public void clearMockServerQueue() throws Exception {
		// This call clears mock server queue.
		try {
			while (mockServer.takeRequest(1, TimeUnit.MILLISECONDS) != null) {
			}
		} catch (InterruptedException e) {
			// Do nothing, it means the queue is empty
		}
	}
	
}
