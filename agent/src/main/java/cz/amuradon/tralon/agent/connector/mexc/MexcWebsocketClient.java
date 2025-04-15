package cz.amuradon.tralon.agent.connector.mexc;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.InjectableValues.Std;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.Trade;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;

@ClientEndpoint
@ApplicationScoped  // XXX Should not it be rather @Dependent per strategy?
@Mexc
@WebsocketClientFactory // Required for proper usage with Instance
public class MexcWebsocketClient implements WebsocketClient {
	
	private static final String SPOT_TRADE_UPDATES_CHANNEL_PREFIX = "spot@public.deals.v3.api@";
	
	private static final String SPOT_DEPTH_UPDATES_CHANNEL_PREFIX = "spot@public.increase.depth.v3.api@";

	private static final String SPOT_ACCOUNT_UPDATES_CHANNEL = "spot@private.account.v3.api";

	private static final String SPOT_ORDER_UPDATES_CHANNEL = "spot@private.orders.v3.api";

	private final String baseUri;
	
	private final RestClient restClient;
	
	private final ObjectMapper mapper;
	
	private Consumer<AccountBalance> accountBalanceCallback;
	
	private Consumer<OrderChange> orderChangeCallback;
	
	private Consumer<OrderBookChange> orderBookChangeCallback;

	private Consumer<Trade> tradeCallback;
	
	private String depthUpdatesChannel = "";

	private String tradeUpdatesChannel = "";
	
	private Session session;
	
	@Inject
	public MexcWebsocketClient(@ConfigProperty(name = "mexc-api.websocket.url") final String baseUri,
			@Mexc RestClient restClient) {
		this.baseUri = baseUri;
		this.restClient = restClient;
		mapper = new ObjectMapper();
		accountBalanceCallback = e -> {};
		orderChangeCallback = e -> {};
		orderBookChangeCallback = e -> {};
	}
	
	private void connect() {
		String listenKey = restClient.userDataStream();
		// TODO obnovit listen kay kazdych 30 minut
		// znovu-pripojit kazdych 24h pri odpojeni 
		try {
			session = ContainerProvider.getWebSocketContainer()
				.connectToServer(this, URI.create(baseUri + "?listenKey=" + listenKey));
		} catch (DeploymentException | IOException e) {
			throw new IllegalStateException("The Websocket client could not be established.", e);
		}
	}
	
	private void subscribe(String channel) {
		try {
			session.getBasicRemote().sendText(String.format(
					"{ \"method\":\"SUBSCRIPTION\", \"params\":[\"%s\"] }",
					channel));
		} catch (IOException e) {
			throw new IllegalStateException("The Websocket client could not subscribe to channels.", e);
		}
	}
	
	// TODO unit test unmarshalling
	@OnMessage
	public void onMessage(String message) {
		try {
			JsonNode tree = mapper.readTree(message);
			JsonNode channelNode = tree.get("c");
			JsonNode data = tree.get("d");
			if (channelNode != null) {
				String channel = channelNode.asText();
				if (depthUpdatesChannel.equalsIgnoreCase(channel)) {
					orderBookChangeCallback.accept(mapper.treeToValue(data, MexcOrderBookChange.class));
				} else if (tradeUpdatesChannel.equalsIgnoreCase(channel)) {
					MexcTradeEventData dealsData = mapper.treeToValue(data, MexcTradeEventData.class);
					for (Trade trade : dealsData.deals()) {
						tradeCallback.accept(trade);
					}
				} else if (SPOT_ACCOUNT_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					accountBalanceCallback.accept(mapper.treeToValue(data, MexcAccountBalanceUpdate.class));
				} else if (SPOT_ORDER_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Std values = new InjectableValues.Std();
					values.addValue("symbol", tree.get("s").asText());
					orderChangeCallback.accept(mapper.readerFor(MexcOrderChange.class).with(values).readValue(data));
				}
			}
		} catch (IOException e) {
			Log.error("The Websocket client could not parse JSON.", e);
		} 
	}

	@Override
	public void onOrderChange(Consumer<OrderChange> callback) {
		if (session == null) {
			connect();
		}
		subscribe(SPOT_ORDER_UPDATES_CHANNEL);
		orderChangeCallback = callback;
	}

	@Override
	public void onOrderBookChange(Consumer<OrderBookChange> callback, String symbol) {
		if (session == null) {
			connect();
		}
		depthUpdatesChannel = SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol;
		subscribe(depthUpdatesChannel);
		orderBookChangeCallback = callback;
	}

	@Override
	public void onAccountBalance(Consumer<AccountBalance> callback) {
		if (session == null) {
			connect();
		}
		subscribe(SPOT_ACCOUNT_UPDATES_CHANNEL);
		accountBalanceCallback = callback;
	}

	@Override
	public void onTrade(Consumer<Trade> callback, String symbol) {
		if (session == null) {
			connect();
		}
		tradeUpdatesChannel = SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol; 
		subscribe(tradeUpdatesChannel);
		tradeCallback = callback;
	}

}
