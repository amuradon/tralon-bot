package cz.amuradon.tralon.agent.connector.mexc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PublicAggreDealsV3ApiItem;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;

import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.NoopWebsocketClientListener;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.Trade;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import cz.amuradon.tralon.agent.connector.WebsocketClientListener;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;

@ClientEndpoint
@Dependent
@Mexc
@WebsocketClientFactory // Required for proper usage with Instance
public class MexcWebsocketClient implements WebsocketClient {
	
	private static final String SPOT_TRADE_UPDATES_CHANNEL_PREFIX = "spot@public.aggre.deals.v3.api.pb@10ms@";
	
	private static final String SPOT_DEPTH_UPDATES_CHANNEL_PREFIX = "spot@public.aggre.depth.v3.api.pb@10ms@";

	private static final String SPOT_ACCOUNT_UPDATES_CHANNEL = "spot@private.account.v3.api.pb";

	private static final String SPOT_ORDER_UPDATES_CHANNEL = "spot@private.orders.v3.api.pb";

	private final String baseUri;
	
	private final RestClient restClient;
	
	private WebsocketClientListener listener;
	
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
		this.listener = new NoopWebsocketClientListener();
		mapper = new ObjectMapper();
		accountBalanceCallback = e -> {};
		orderChangeCallback = e -> {};
		orderBookChangeCallback = e -> {};
	}
	
	public void setListener(WebsocketClientListener listener) {
		this.listener = listener;
		listener.setTransformer(b -> {
			try {
				PushDataV3ApiWrapper data = PushDataV3ApiWrapper.parseFrom(b);
				ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
				data.writeDelimitedTo(bytesStream);
				return bytesStream.toByteArray();
			} catch (IOException e) {
				Log.error("Could not write PB message", e);
				return new byte[0];
			}
		});
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
		Log.infof("Subscribing: %s", channel);
		try {
			// TODO validovat, jestli kanalu byla uspesna 
			session.getBasicRemote().sendText(String.format(
					"{ \"method\":\"SUBSCRIPTION\", \"params\":[\"%s\"] }",
					channel));
		} catch (IOException e) {
			throw new IllegalStateException("The Websocket client could not subscribe to channels.", e);
		}
	}
	
	@OnMessage
	public void onMessage(String message) {
		Log.infof("Message: %s", message);
		try {
			JsonNode tree = mapper.readTree(message);
			JsonNode msg = tree.get("msg");
			if (msg != null && msg.asText().startsWith("Not")) {
				throw new IllegalStateException("Websocket subscription failed: " + message);
			}
		} catch (IOException e) {
			Log.error("The Websocket client could not parse JSON.", e);
		} 
	}

	// TODO unit test unmarshalling
	@OnMessage
	public void onMessage(byte[] message) {
		try {
			PushDataV3ApiWrapper data = PushDataV3ApiWrapper.parseFrom(message);
			final String symbol = data.getSymbol();
			if (data.getChannel().equalsIgnoreCase(depthUpdatesChannel)) {
				listener.onOrderBookUpdate(symbol, message);
				orderBookChangeCallback.accept(new MexcOrderBookChange(data.getPublicAggreDepths()));
			} else if (data.getChannel().equalsIgnoreCase(tradeUpdatesChannel)) {
				listener.onTrade(symbol, message);
				for (PublicAggreDealsV3ApiItem trade : data.getPublicAggreDeals().getDealsList()) {
					tradeCallback.accept(new MexcTrade(trade));
				}
			} else if (data.getChannel().equalsIgnoreCase(SPOT_ACCOUNT_UPDATES_CHANNEL)) {
				listener.onAccountBalanceUpdate(message);
				accountBalanceCallback.accept(new MexcAccountBalanceUpdate(data.getPrivateAccount()));
			} else if (data.getChannel().equalsIgnoreCase(SPOT_ORDER_UPDATES_CHANNEL)) {
				listener.onOrderUpdate(message);
				orderChangeCallback.accept(new MexcOrderChange(symbol, data.getPrivateOrders()));
			}
		} catch (InvalidProtocolBufferException e) {
			Log.error("The Websocket client could not parse Protobuf.", e);
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
