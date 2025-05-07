package cz.amuradon.tralon.agent.connector.binance;

import static cz.amuradon.tralon.agent.connector.RequestUtils.param;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.binance.connector.client.SpotClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.ListenKey;
import cz.amuradon.tralon.agent.connector.NewOrderResponse;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.OrderBookResponseImpl;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.RestClientListener;
import cz.amuradon.tralon.agent.model.Order;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@Binance
@RestClientFactory // Required for proper usage with Instance
public class BinanceRestClient implements RestClient {

	private final SpotClient spotClient;
	
	private final ObjectMapper mapper;
	
	private final Map<String, Integer> quantityScales;
	private final Map<String, Integer> priceScales;
	
	@Inject
	public BinanceRestClient(SpotClient spotClient) {
		this.spotClient = spotClient;
		mapper = new ObjectMapper();
		quantityScales = new ConcurrentHashMap<>();
		priceScales = new ConcurrentHashMap<>();
	}
	
	@Override
	public NewOrderSymbolBuilder newOrder() {
		return new BinanceNewOrderSymbolBuilder();
	}

	@Override
	public void cancelOrder(String orderId, String symbol) {
		spotClient.createTrade()
			.cancelOrder(param("symbol", symbol).param("orderId", orderId));
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		final String response = spotClient.createTrade().getOpenOrders(param("symbol", symbol));
		try {
			return mapper.readValue(response, new TypeReference<List<BinanceOrder>>() { })
					.stream().collect(Collectors.toMap(o -> o.orderId(), o -> o));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read open orders.", e);
		}
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		final String response = spotClient.createTrade().account(new LinkedHashMap<>());
		try {
			return mapper.readValue(response, BinanceAccountInformation.class).balances();
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read account information.", e);
		}
	}
	
	@Override
	public OrderBookResponse orderBook(String symbol) {
		try {
			String response = spotClient.createMarket().depth(param("symbol", symbol).param("limit", 5000));
			BinanceOrderBookResponse orderBookResponse = mapper.readValue(response, BinanceOrderBookResponse.class);
			return new OrderBookResponseImpl(orderBookResponse.lastUpdateId(),
					orderBookResponse.asks(), orderBookResponse.bids());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read order book.", e);
		}
	}
	
	@Override
	public cz.amuradon.tralon.agent.connector.SymbolInfo cacheSymbolDetails(String symbol) {
		if (priceScales.get(symbol) != null && quantityScales.get(symbol) != null) {
			return new cz.amuradon.tralon.agent.connector.SymbolInfo(priceScales.get(symbol));
		}

		try {
			String response = spotClient.createMarket().exchangeInfo(param("symbol", symbol));
			ExchangeInfo exchangeInfo = mapper.readValue(response, ExchangeInfo.class);
			int priceScale = 0;
			for (SymbolInfo symbolInfo : exchangeInfo.symbols()) {
				if (symbol.equalsIgnoreCase(symbolInfo.symbol())) {
					for (Filter filter : symbolInfo.filters())
						if ("LOT_SIZE".equalsIgnoreCase(filter.filterType)) {
							quantityScales.put(symbol, new BigDecimal(filter.get("stepSize")).stripTrailingZeros().scale());
						} else if ("PRICE_FILTER".equalsIgnoreCase(filter.filterType)) {
							priceScale = new BigDecimal(filter.get("tickSize")).stripTrailingZeros().scale();
							priceScales.put(symbol, priceScale);
						}
					break;
				}
			}
			return new cz.amuradon.tralon.agent.connector.SymbolInfo(priceScale);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read exchange info.", e);
		}
		
	}
	
	@Override
	public String userDataStream() {
		try {
			return mapper.readValue(spotClient.createUserData().createListenKey(), ListenKey.class).listenKey();
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read listen key.", e);
		}
	}
	
	@Override
	public void setListener(RestClientListener listener) {
		// TODO Implementovat az bude bez SDK v low-level
	}

	public final class BinanceNewOrderSymbolBuilder implements NewOrderSymbolBuilder {

		@Override
		public NewOrderBuilder symbol(String symbol) {
			return new BinanceNewOrderBuilder(symbol);
		}

	}

	public final class BinanceNewOrderBuilder implements NewOrderBuilder {

		private final Map<String, Object> parameters = new HashMap<>();
		private String symbol;
		private BigDecimal quantity;
		private BigDecimal price;
		
		public BinanceNewOrderBuilder(String symbol) {
			this.symbol = symbol;
			parameters.put("symbol", symbol);
		}

		@Override
		public NewOrderBuilder clientOrderId(String clientOrderId) {
			parameters.put("newClientOrderId", clientOrderId);
			return this;
		}

		@Override
		public NewOrderBuilder side(Side side) {
			parameters.put("side", side.name());
			return this;
		}

		@Override
		public NewOrderBuilder price(BigDecimal price) {
			this.price = price;
			return this;
		}

		@Override
		public NewOrderBuilder size(BigDecimal size) {
			this.quantity = size;
			return this;
		}

		@Override
		public NewOrderBuilder type(OrderType type) {
			parameters.put("type", type.name());
			if (type == OrderType.LIMIT) {
				parameters.put("timeInForce", "GTC");
			}
			return this;
		}
		
		@Override
		public NewOrderBuilder timestamp(long timestamp) {
			parameters.put("timestamp", timestamp);
			return this;
		}

		@Override
		public NewOrderBuilder recvWindow(long recvWindow) {
			parameters.put("recvWindow", recvWindow);
			return this;
		}

		@Override
		public NewOrderBuilder signParams() {
			Log.warn("New order signing not supported as of now as Binance SDK client does not expose it");
			return this;
		}

		@Override
		public NewOrderResponse send() {
			if (symbol == null) {
				throw new IllegalArgumentException("Could not send order - symbol is missing.");
			}
			
			Integer quantityScale = quantityScales.get(symbol);
			parameters.put("quantity", quantity.setScale(quantityScale, RoundingMode.HALF_UP).toPlainString());

			Integer priceScale = priceScales.get(symbol);
			parameters.put("price", price.setScale(priceScale, RoundingMode.HALF_UP).toPlainString());
			
			return new NewOrderResponse(true, spotClient.createTrade().newOrder(parameters), null);
		}

	}


}
