package cz.amuradon.tralon.agent.connector.mexc;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

import io.fabric8.mockwebserver.DefaultMockServer;
import io.fabric8.mockwebserver.MockServer;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.http.Headers;
import io.fabric8.mockwebserver.http.RecordedRequest;
import io.fabric8.mockwebserver.utils.ResponseProvider;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MexcExchangeMock implements QuarkusTestResourceLifecycleManager {

	private DefaultMockServer server;

	@Override
	public void inject(TestInjector testInjector) {
		testInjector.injectIntoFields(server,
				new TestInjector.AnnotatedAndMatchesType(InjectHttpServerMock.class, MockServer.class));
	}
	
	@Override
	public Map<String, String> start() {
		final String listenKey = "pqia91ma19a5s61cv6a81va65sdf19v8a65a1a5s61cv6a81va65sdf19v8a65a1";
		HashMap<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
		server = new DefaultMockServer(new io.fabric8.mockwebserver.Context(), new MockWebServer(), responses,
				new UrlParamsIgnoringDispatcher(responses), false);
		
		expectGet("/depth", () ->
		String.format("""
		{"lastUpdateId":10050,"bids":[["0.2900","5.55"],["0.2800","4.44"],["0.2700","3.33"]],
		"asks":[["0.3000","6.66"],["0.3100","7.77"],["0.3200","8.88"]],"timestamp":%d}		
		""", new Date().getTime()));
		
		/*
		expectPost("/fapi/v1/listenKey", () ->
			String.format("""
			{
				"listenKey": "%s"
			}
			""", listenKey));
	
		expectGet("/fapi/v1/time", () ->
			String.format("""
			{
				"serverTime": %d
			}		
			""", new Date().getTime()));
		
		// Simplified
		expectGet("/fapi/v2/positionRisk", () ->
			"""
			[
			    {
			        "marginType": "isolated", 
			        "leverage": "10", 
			        "symbol": "BTCUSDT" 
			    }
			]		
			""");

		// Simplified
		expectGet("/fapi/v1/exchangeInfo", () ->
			"""
			{
			    "exchangeFilters": [],
			    "symbols": [
			        {
						"symbol": "BTCUSDT",
						"contractType": "PERPETUAL",
						"status": "TRADING",
						"filters": [
							{
								"filterType": "PRICE_FILTER",
								"tickSize": "0.10",
								"minPrice": "556.80",
								"maxPrice": "4529764"
							},
							{
								"minQty": "0.001",
								"filterType": "LOT_SIZE",
								"maxQty": "1000",
								"stepSize": "0.001"
							},
							{
								"maxQty": "120",
								"stepSize": "0.001",
								"filterType": "MARKET_LOT_SIZE",
								"minQty": "0.001"
							},
							{
								"filterType": "MAX_NUM_ORDERS",
								"limit": 200
							},
							{
								"limit": 10,
								"filterType": "MAX_NUM_ALGO_ORDERS"
							},
							{
								"notional": "100",
								"filterType": "MIN_NOTIONAL"
							},
							{
								"multiplierUp": "1.0500",
								"filterType": "PERCENT_PRICE",
								"multiplierDecimal": "4",
								"multiplierDown": "0.9500"
							}
						],
						"orderTypes": [
							"LIMIT",
							"MARKET",
							"STOP",
							"STOP_MARKET",
							"TAKE_PROFIT",
							"TAKE_PROFIT_MARKET",
							"TRAILING_STOP_MARKET"
						],
						"timeInForce": [
							"GTC",
							"IOC",
							"FOK",
							"GTX",
							"GTD"
						]
					}
			    ],
			    "timezone": "UTC" 
			}	
			""");
		
		// Simplified
		expectGet("/fapi/v2/balance", () ->
			"""
			[
			    {
			        "asset": "USDT",
			        "availableBalance": "15000.00"
			    }
			]
			""");
		
		// Response not used in PROD code yet
		expectPost("/fapi/v1/marginType", () ->
			"""
			{
			    "code": 200,
			    "msg": "success"
			}
			""");
		
		// Response not used in PROD code yet
		expectPost("/fapi/v1/leverage", () ->
			"""
			{
			    "leverage": 10,
			    "maxNotionalValue": "1000000",
			    "symbol": "BTCUSDT"
			}
			""");
		*/
		
		// Simplified (only in code used fields)
		
		server.expect().post().withPath("/order?newClientOrderId=ValidNewOrder")
			.andReply(new MyResponseProvider(200, 
					() -> "{\"orderId\": \"orderId\"}"))
			.always();
		server.expect().post().withPath("/order?newClientOrderId=NoValidTradePriceException")
			.andReply(new MyResponseProvider(400, 
					() -> "{\"code\":\"30010\", \"msg\":\"The price cannot be higher than 0.05USDT\"}"))
			.always();
		server.expect().post().withPath("/order?newClientOrderId=TradeDirectionNotAllowedException")
			.andReply(new MyResponseProvider(400, 
					() -> "{\"code\":\"30001\", \"msg\":\"The price cannot be higher than 0.05USDT\"}"))
			.always();
		server.expect().post().withPath("/order?newClientOrderId=TooManyRequestsError")
			.andReply(new MyResponseProvider(429, 
					() -> "{\"code\":\"429\", \"msg\":\"Too many requests\"}"))
			.always();

		/*
		server.expect().withPath("/ws/" + listenKey).andUpgradeToWebSocket()
			.open()
			.immediately().andEmit(listenKey)
			.done().always();
		*/
		
		server.start();
		
		return Map.of(
//				"binance.futures.websocket.url", "ws://localhost:" + server.getPort(),
				"quarkus.rest-client.mexc-api.url", "http://localhost:" + server.getPort()
				);
	}

	@Override
	public void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	private void expectGet(String path, Supplier<Object> body) {
		server.expect().withPath(path)
			.andReply(new MyResponseProvider(body)).always();
	}

	private void expectPost(String path, Supplier<Object> body) {
		server.expect().post().withPath(path)
			.andReply(new MyResponseProvider(body)).always();
	}
	
	private static class MyResponseProvider implements ResponseProvider<Object> {

		private final int statusCode;
		private final Supplier<Object> bodySupplier;
				
		private Headers headers = new Headers.Builder().add("Content-Type", "application/json").build();
		
		public MyResponseProvider(Supplier<Object> bodySupplier) {
			this(200, bodySupplier);
		}

		public MyResponseProvider(int statusCode, Supplier<Object> bodySupplier) {
			this.statusCode = statusCode;
			this.bodySupplier = bodySupplier;
		}

		@Override
		public Object getBody(RecordedRequest request) {
			return bodySupplier.get();
		}

		@Override
		public int getStatusCode(RecordedRequest request) {
			return statusCode;
		}

		@Override
		public Headers getHeaders() {
			return headers;
		}

		@Override
		public void setHeaders(Headers headers) {
			this.headers = headers;
		}
		
	}
}
