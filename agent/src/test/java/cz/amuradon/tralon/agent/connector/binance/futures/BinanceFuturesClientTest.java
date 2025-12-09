package cz.amuradon.tralon.agent.connector.binance.futures;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import cz.amuradon.tralon.agent.connector.Kline;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class BinanceFuturesClientTest {

	@RestClient
	BinanceFuturesClient client;
	
	@Test
	public void testKline() {
		System.out.println(Arrays.stream(client.klines("FHEUSDT", "1m", 100))
				.map(Kline::toString).collect(Collectors.joining("\n")));
	}
	
}
