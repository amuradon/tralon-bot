package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.Test;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

// TODO
//@QuarkusTest
public class MexcWebsocketClientIntegrationTest {
	
	@Mexc
	@RestClientFactory
	@Inject
	MexcRestClientAdapter restClient;
	
	@Mexc
	@WebsocketClientFactory
	@Inject
	MexcWebsocketClient client;
	
//	@Test
	public void test() throws InterruptedException {
		client.onOrderChange(System.out::println);
		
		restClient.cacheSymbolDetails("JINXDOGUSDT");
		restClient.newOrder().symbol("JINXDOGUSDT").price(new BigDecimal("0.00004")).side(Side.BUY)
			.size(new BigDecimal(250000)).type(OrderType.LIMIT).clientOrderId("JINXDOGUSDT-" + new Date().getTime())
			.send();
		
//		client.onOrderBookChange(System.out::println, "JINXDOGUSDT");
//		client.onAccountBalance(System.out::println);
//		client.onTrade(System.out::println, "JINXDOGUSDT");
		Thread.sleep(600000);
	}
}
