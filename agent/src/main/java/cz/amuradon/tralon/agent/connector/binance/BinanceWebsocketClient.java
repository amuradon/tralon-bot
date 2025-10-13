package cz.amuradon.tralon.agent.connector.binance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.NoopWebsocketClientListener;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.Trade;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import cz.amuradon.tralon.agent.connector.WebsocketClientListener;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;

@Dependent
@Binance
@WebsocketClientFactory // Required for proper usage with Instance
public class BinanceWebsocketClient implements WebsocketClient {

	private final SpotClient spotClient;
	
	private final WebSocketStreamClient client;
	
	private final ObjectMapper mapper;
	
	private Consumer<AccountBalance> accountBalanceCallback;
	private Consumer<OrderChange> orderChangeCallback;
	
	private boolean connected;
	
	private WebsocketClientListener listener;
	
	public BinanceWebsocketClient(final SpotClient spotClient) {
		this.spotClient = spotClient;
		mapper = new ObjectMapper();
		accountBalanceCallback = e -> {};
		orderChangeCallback = e -> {};
		client = new WebSocketStreamClientImpl();
		listener = new NoopWebsocketClientListener();
	}
	
	private boolean connect() {
		String listenKey;
		try {
			listenKey = mapper.readTree(spotClient.createUserData().createListenKey()).get("listenKey").asText();
	        client.listenUserStream(listenKey, ((event) -> {
	            Log.debugf("User stream event: %s", event);
	            try {
					JsonNode tree = mapper.readTree(event);
					String eventType = tree.get("e").asText();
					if ("outboundAccountPosition".equalsIgnoreCase(eventType)) {
						listener.onAccountBalanceUpdate(event.getBytes(StandardCharsets.UTF_8));
						mapper.treeToValue(tree.get("B"), new TypeReference<List<BinanceAccountBalanceUpdate>>() { })
							.stream().forEach(b -> accountBalanceCallback.accept(b));
						
					} else if ("executionReport".equalsIgnoreCase(eventType)) {
						listener.onOrderUpdate(event.getBytes(StandardCharsets.UTF_8));
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
	        return true;
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not parse Websocket JSON", e);
		}
	}
	
	@Override
	public void onOrderChange(Consumer<OrderChange> callback) {
		if (!connected) {
			connected = connect();
		}
		orderChangeCallback = callback;
	}

	@Override
	public void onOrderBookChange(Consumer<OrderBookChange> callback, String symbol) {
		if (!connected) {
			connected = connect();
		}
		client.diffDepthStream(symbol, 100, data -> {
			try {
				listener.onOrderBookUpdate(symbol, data.getBytes(StandardCharsets.UTF_8));
				callback.accept(mapper.readValue(data, BinanceOrderBookChange.class));
			} catch (JsonProcessingException e) {
				throw new IllegalStateException("Could not parse Websocket JSON", e);
			}
		});
	}

	@Override
	public void onAccountBalance(Consumer<AccountBalance> callback) {
		if (!connected) {
			connected = connect();
		}
		accountBalanceCallback = callback;
	}

	@Override
	public void onTrade(Consumer<Trade> callback, String symbol) {
		if (!connected) {
			connected = connect();
		}
		client.tradeStream(symbol, data -> {
			try {
				listener.onTrade(symbol, data.getBytes(StandardCharsets.UTF_8));
				callback.accept(mapper.readValue(data, BinanceTrade.class));
			} catch (JsonProcessingException e) {
				throw new IllegalStateException("Could not parse Websocket JSON", e);
			}
		});
		
	}

	@Override
	public void setListener(WebsocketClientListener listener) {
		this.listener = listener;
	}

	@Override
	public void close() {
		client.closeAllConnections();
	}

}
