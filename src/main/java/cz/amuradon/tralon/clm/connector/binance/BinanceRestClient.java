package cz.amuradon.tralon.clm.connector.binance;

import static cz.amuradon.tralon.clm.connector.RequestUtils.param;

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

import cz.amuradon.tralon.clm.OrderType;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.ListenKey;
import cz.amuradon.tralon.clm.connector.OrderBookResponse;
import cz.amuradon.tralon.clm.connector.OrderBookResponseImpl;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.RestClientFactory;
import cz.amuradon.tralon.clm.model.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
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
	public NewOrderBuilder newOrder() {
		return new BinanceNewOrderBuilder();
	}

	@Override
	public void cancelOrder(Order order) {
		spotClient.createTrade()
			.cancelOrder(param("symbol", order.symbol()).param("orderId", order.orderId()));
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		final String response = spotClient.createTrade().getOpenOrders(param("symbol", symbol));
		try {
			return mapper.readValue(response, new TypeReference<List<BinanceMexcOrder>>() { })
					.stream().collect(Collectors.toMap(o -> o.orderId(), o -> o));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read open orders.", e);
		}
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		final String response = spotClient.createTrade().account(new LinkedHashMap<>());
		try {
			return mapper.readValue(response, BinanceMexcAccountInformation.class).balances();
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read account information.", e);
		}
	}
	
	@Override
	public OrderBookResponse orderBook(String symbol) {
		try {
			String response = spotClient.createMarket().depth(param("symbol", symbol).param("limit", 5000));
			BinanceOrderBookResponse orderBookResponse = mapper.readValue(response, BinanceOrderBookResponse.class);
			return new OrderBookResponseImpl(orderBookResponse.sequence(),
					orderBookResponse.asks(), orderBookResponse.bids());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read order book.", e);
		}
	}
	
	@Override
	public void cacheSymbolDetails(String symbol) {
		if (priceScales.get(symbol) != null && quantityScales.get(symbol) != null) {
			return;
		}

		try {
			String response = spotClient.createMarket().exchangeInfo(param("symbol", symbol));
			ExchangeInfo exchangeInfo = mapper.readValue(response, ExchangeInfo.class);
			for (SymbolInfo symbolInfo : exchangeInfo.symbols()) {
				if (symbol.equalsIgnoreCase(symbolInfo.symbol())) {
					for (Filter filter : symbolInfo.filters())
						if ("LOT_SIZE".equalsIgnoreCase(filter.filterType)) {
							quantityScales.put(symbol, new BigDecimal(filter.get("stepSize")).stripTrailingZeros().scale());
						} else if ("PRICE_FILTER".equalsIgnoreCase(filter.filterType)) {
							priceScales.put(symbol, new BigDecimal(filter.get("tickSize")).stripTrailingZeros().scale());
						}
					break;
				}
			}
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

	public final class BinanceNewOrderBuilder implements NewOrderBuilder {

		private final Map<String, Object> parameters = new HashMap<>();
		private String symbol;
		private BigDecimal quantity;
		private BigDecimal price;
		
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
		public NewOrderBuilder symbol(String symbol) {
			parameters.put("symbol", symbol);
			this.symbol = symbol;
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
		public String send() {
			if (symbol == null) {
				throw new IllegalArgumentException("Could not send order - symbol is missing.");
			}
			
			Integer quantityScale = quantityScales.get(symbol);
			parameters.put("quantity", quantity.setScale(quantityScale, RoundingMode.HALF_UP).toPlainString());

			Integer priceScale = priceScales.get(symbol);
			parameters.put("price", price.setScale(priceScale, RoundingMode.HALF_UP).toPlainString());
			
			return spotClient.createTrade().newOrder(parameters);
		}
	}

}
