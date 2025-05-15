package cz.amuradon.tralon.agent.connector.kucoin;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest.OrderCreateApiRequestBuilder;
import com.kucoin.sdk.rest.response.SymbolResponse;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.OrderBookResponseImpl;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.RestClientListener;
import cz.amuradon.tralon.agent.connector.SymbolInfo;
import cz.amuradon.tralon.agent.model.Order;
import cz.amuradon.tralon.agent.model.OrderImpl;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@Kucoin
@RestClientFactory // Required for proper usage with Instance
public class KucoinRestClient implements RestClient {

	private final com.kucoin.sdk.KucoinRestClient restClient;
	
	private final Map<String, Integer> sizeScales;
	private final Map<String, Integer> priceScales;
	
	@Inject
	public KucoinRestClient(final com.kucoin.sdk.KucoinRestClient restClient) {
		this.restClient = restClient;
		sizeScales = new ConcurrentHashMap<>();
		priceScales = new ConcurrentHashMap<>();
	}
	
	@Override
	public NewOrderSymbolBuilder newOrder() {
		return new KucoinNewOrderSymbolBuilder();
	}
	
	@Override
	public void cancelOrder(String orderId, String symbol) {
		try {
			restClient.orderAPI().cancelOrder(orderId);
		} catch (IOException e) {
			throw new IllegalStateException("Could not send cancel order request.", e);
		}
	}
	
	@Override
	public Map<String, Order> listOrders(String symbol) {
		try {
			return restClient.orderAPI().listOrders(symbol, null, null, null, "active", null, null, 20, 1).getItems().stream()
			.collect(Collectors.toMap(r -> r.getId(), r ->
					new OrderImpl(r.getId(), r.getSymbol(), Side.getValue(r.getSide()), r.getSize(), r.getPrice())));
		} catch (IOException e) {
			throw new IllegalStateException("Could not list orders.", e);
		}
	}
	
	@Override
	public List<AccountBalance> listBalances() {
		try {
			return restClient.accountAPI().listAccounts(null, "trade").stream()
					.map(b -> new KucoinAccountBalance(b)).collect(Collectors.toList());
		} catch (IOException e) {
			throw new IllegalStateException("Could not account balances.", e);
		}
	}

	@Override
	public OrderBookResponse orderBook(String symbol) {
		com.kucoin.sdk.rest.response.OrderBookResponse response;
		try {
			response = restClient.orderBookAPI().getAllLevel2OrderBook(symbol);
			return new OrderBookResponseImpl(Long.parseLong(response.getSequence()), response.getAsks(), response.getBids()); 
		} catch (IOException e) {
			throw new IllegalStateException("Could not get order book.", e);
		}
	}
	
	@Override
	public SymbolInfo cacheSymbolDetails(String symbol) {
		if (priceScales.get(symbol) != null && sizeScales.get(symbol) != null) {
			return new SymbolInfo(priceScales.get(symbol));
		}

		try {
			SymbolResponse symbolDetails = restClient.symbolAPI().getSymbolDetail(symbol);
			int priceScale = symbolDetails.getPriceIncrement().stripTrailingZeros().scale();
			priceScales.put(symbol, priceScale);
			sizeScales.put(symbol, symbolDetails.getBaseIncrement().stripTrailingZeros().scale());
			return new SymbolInfo(priceScale);
		} catch (IOException e) {
			throw new IllegalStateException("Could not get symbol details.", e);
		}
	}
	
	@Override
	public String userDataStream() {
		// Kucoin's websocket clients handle this internally, REST client does not seem to even provide it
		throw new UnsupportedOperationException("KucoinRestClient#userDataStream not available. Was not needed so far");
	}
	
	@Override
	public void setListener(RestClientListener listener) {
		// TODO Implementovat az bude bez SDK v low-level
	}
	
	public final class KucoinNewOrderSymbolBuilder implements NewOrderSymbolBuilder {
		@Override
		public NewOrderBuilder symbol(String symbol) {
			return new KucoinNewOrderBuilder(symbol);
		}
	}
	public final class KucoinNewOrderBuilder implements NewOrderBuilder {
		
		private OrderCreateApiRequestBuilder builder = OrderCreateApiRequest.builder();
		private String symbol;
		private BigDecimal size;
		private BigDecimal price;

		public KucoinNewOrderBuilder(String symbol) {
			this.symbol = symbol;
			builder.symbol(symbol);
		}

		@Override
		public NewOrderBuilder clientOrderId(String clientOrderId) {
			builder.clientOid(clientOrderId);
			return this;
		}

		@Override
		public NewOrderBuilder side(Side side) {
			builder.side(side.name().toLowerCase());
			return this;
		}

		@Override
		public NewOrderBuilder price(BigDecimal price) {
			this.price = price;
			return this;
		}

		@Override
		public NewOrderBuilder size(BigDecimal size) {
			this.size = size;
			return this;
		}

		@Override
		public NewOrderBuilder type(OrderType type) {
			builder.type(type.name().toLowerCase());
			return this;
		}
		
		@Override
		public NewOrderBuilder timestamp(long timestamp) {
			Log.warn("New order 'timestamp' parameter not supported as of now as Kucoin SDK client does not expose it");
			return this;
		}

		@Override
		public NewOrderBuilder recvWindow(long recvWindow) {
			Log.warn("New order 'recvWindow' parameter not supported as of now as Kucoin SDK client does not expose it");
			return this;
		}

		@Override
		public NewOrderBuilder signParams() {
			Log.warn("New order signing not supported as of now as Kucoin SDK client does not expose it");
			return this;
		}

		@Override
		public String send() {
			if (symbol == null) {
				throw new IllegalArgumentException("Could not send order - symbol is missing.");
			}
			
			Integer quantityScale = sizeScales.get(symbol);
			builder.size(size.setScale(quantityScale, RoundingMode.HALF_UP));

			Integer priceScale = priceScales.get(symbol);
			builder.price(price.setScale(priceScale, RoundingMode.HALF_UP));

			try {
				return restClient.orderAPI().createOrder(builder.build()).getOrderId();
			} catch (IOException e) {
				throw new IllegalStateException("Could not send new order request.", e);
			}
		}
		
	}

}
