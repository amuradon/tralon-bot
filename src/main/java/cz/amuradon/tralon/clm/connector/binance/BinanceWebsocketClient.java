package cz.amuradon.tralon.clm.connector.binance;

import java.util.List;
import java.util.function.Consumer;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.OrderBookChange;
import cz.amuradon.tralon.clm.connector.OrderChange;
import cz.amuradon.tralon.clm.connector.WebsocketClient;
import cz.amuradon.tralon.clm.connector.WebsocketClientFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Binance
@WebsocketClientFactory // Required for proper usage with Instance
public class BinanceWebsocketClient implements WebsocketClient {

	private final SpotClient spotClient;
	
	private final WebSocketStreamClient client;
	
	private final ObjectMapper mapper;
	
	private Consumer<AccountBalance> accountBalanceCallback;
	private Consumer<OrderChange> orderChangeCallback;
	
	public BinanceWebsocketClient(final SpotClient spotClient) {
		this.spotClient = spotClient;
		mapper = new ObjectMapper();
		accountBalanceCallback = e -> {};
		orderChangeCallback = e -> {};
		client = new WebSocketStreamClientImpl();
		
		String listenKey;
		try {
			listenKey = mapper.readTree(spotClient.createUserData().createListenKey()).get("listenKey").asText();
	        System.out.println("listenKey:" + listenKey);
	        client.listenUserStream(listenKey, ((event) -> {
	            Log.debugf("User stream event: %s", event);
	            try {
					JsonNode tree = mapper.readTree(event);
					String eventType = tree.get("e").asText();
					if ("outboundAccountPosition".equalsIgnoreCase(eventType)) {
						mapper.treeToValue(tree.get("B"), new TypeReference<List<BinanceAccountBalanceUpdate>>() { })
							.stream().forEach(b -> accountBalanceCallback.accept(b));
						
					} else if ("executionReport".equalsIgnoreCase(eventType)) {
						orderChangeCallback.accept(mapper.readValue(event, BinanceOrderChange.class));
					}
				} catch (JsonProcessingException e) {
					Log.error("Could not parse user stream event", e);
				}
	            
	            // TODO rozdelit stream
	            // obnovovat listen key kazdych 30 minut - dop. Binance
	            // Po 24 h se zavre -> obnovit
	            // reagovat na close, failure atd.
	        }));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not parse Websocket JSON", e);
		}
	}
	
	@Override
	public void onOrderChange(Consumer<OrderChange> callback) {
		orderChangeCallback = callback;
	}

	@Override
	public void onOrderBookChange(Consumer<OrderBookChange> callback, String symbol) {
		client.diffDepthStream(symbol, 100, data -> {
			try {
				callback.accept(mapper.readValue(data, BinanceOrderBookChange.class));
			} catch (JsonProcessingException e) {
				throw new IllegalStateException("Could not parse Websocket JSON", e);
			}
		});
	}

	@Override
	public void onAccountBalance(Consumer<AccountBalance> callback) {
		accountBalanceCallback = callback;
	}

}
