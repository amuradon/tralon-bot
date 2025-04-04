package cz.amuradon.tralon.clm.connector.mexc;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.OrderBookChange;
import cz.amuradon.tralon.clm.connector.OrderChange;
import cz.amuradon.tralon.clm.connector.WebsocketClient;
import cz.amuradon.tralon.clm.connector.WebsocketClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

@ClientEndpoint
@ApplicationScoped
@Mexc
@WebsocketClientFactory // Required for proper usage with Instance
public class MexcWebsocketClient implements WebsocketClient {
	
	public static final String SPOT_TRADE_UPDATES_CHANNEL_PREFIX = "spot@public.deals.v3.api@";

	public static final String SPOT_DEPTH_UPDATES_CHANNEL_PREFIX = "spot@public.increase.depth.v3.api@";

	public static final String SPOT_ACCOUNT_UPDATES_CHANNEL = "spot@private.account.v3.api";

	public static final String SPOT_ORDER_UPDATES_CHANNEL = "spot@private.orders.v3.api";

	private final String baseUri;
	
	private final MexcWebsocketListener websocketListener;
	
	private String tradeUpdatesChannel;

	private String depthUpdatesChannel;
	
	@Inject
	public MexcWebsocketClient(@ConfigProperty(name = "mexc-api.websocket.url") final String baseUri,
			final MexcWebsocketListener websocketListener) {
		this.baseUri = baseUri;
		this.websocketListener = websocketListener;
		
	}
	
	public void connect(String listenKey, String symbol) {
		tradeUpdatesChannel = SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol;
		depthUpdatesChannel = SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol;
		try {
			ContainerProvider.getWebSocketContainer()
				.connectToServer(this, URI.create(baseUri + "?listenKey=" + listenKey));
		} catch (DeploymentException | IOException e) {
			throw new IllegalStateException("The Websocket client could not be established.", e);
		}
	}
	
	@OnOpen
	public void open(Session session) {
		try {
			session.getBasicRemote().sendText(String.format(
					"{ \"method\":\"SUBSCRIPTION\", \"params\":[\"%s\", \"%s\", \"%s\", \"%s\"] }",
					SPOT_ACCOUNT_UPDATES_CHANNEL, SPOT_ORDER_UPDATES_CHANNEL,
					depthUpdatesChannel, tradeUpdatesChannel));
		} catch (IOException e) {
			throw new IllegalStateException("The Websocket client could not subscribe to channels.", e);
		}
	}
	
	@OnMessage
	public void onMessage(String message) {
		websocketListener.onMessage(message);
	}

	@Override
	public void onOrderChange(Consumer<OrderChange> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onLevel2Data(Consumer<OrderBookChange> callback, String symbol) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onAccountBalance(Consumer<AccountBalance> callback) {
		// TODO Auto-generated method stub
		
	}

}
