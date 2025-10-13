package cz.amuradon.tralon.agent.connector.mexc;

import static cz.amuradon.tralon.agent.connector.RequestUtils.param;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.NoopRestClientListener;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.OrderBookResponseImpl;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.RestClientListener;
import cz.amuradon.tralon.agent.connector.Ticker;
import cz.amuradon.tralon.agent.model.Order;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@Mexc
@RestClientFactory // Required for proper usage with Instance
public class MexcRestClientAdapter implements RestClient {

	private static final String HMAC_SHA256 = "HmacSHA256";
	
	private final MexcClient mexcClient;
	
	private RestClientListener listener;
	
	private Mac mac;
	
	private final ObjectMapper objectMapper;
	
	private final Map<String, Integer> quantityScales;
	private final Map<String, Integer> priceScales;
	
	@Inject
	public MexcRestClientAdapter(@ConfigProperty(name = "mexc.secretKey") final String secretKey,
			@org.eclipse.microprofile.rest.client.inject.RestClient final MexcClient mexcClient,
			final ObjectMapper objectMapper) {
		this(secretKey, mexcClient, objectMapper, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
	}
	
	// For testing
	MexcRestClientAdapter(final String secretKey,
			final MexcClient mexcClient,
			final ObjectMapper objectMapper,
			final Map<String, Integer> quantityScales,
			final Map<String, Integer> priceScales) {
		this.mexcClient = mexcClient;
		this.listener = new NoopRestClientListener();
		this.objectMapper = objectMapper;
		try {
			mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secretKey.getBytes(), HMAC_SHA256));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Could not setup encoder", e);
		}
		this.quantityScales = quantityScales;
		this.priceScales = priceScales;
	}
	
	@Override
	public void cancelOrder(String orderId, String symbol) {
    	mexcClient.cancelOrder(signQueryParams(
    			param("timestamp", new Date().getTime())
    			.param("symbol", symbol)
    			.param("orderId", orderId)));
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		return mexcClient.openOrders(signQueryParams(param("timestamp", new Date().getTime()).param("symbol", symbol)))
				.stream().collect(Collectors.toMap(o -> o.orderId(), o -> o));
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		return mexcClient.listBalances(signQueryParams(param("timestamp", new Date().getTime())));
	}

	@Override
	public OrderBookResponse orderBook(String symbol) {
		try {
			String response = mexcClient.orderBook(symbol);
			listener.onOrderBook(symbol, response);
			MexcOrderBookResponse mexcOrderBookResponse = objectMapper.readValue(response, MexcOrderBookResponse.class);
			return new OrderBookResponseImpl(mexcOrderBookResponse.lastUpdateId(),
					mexcOrderBookResponse.asks(), mexcOrderBookResponse.bids());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read JSON.", e);
		}
	}

	@Override
	public cz.amuradon.tralon.agent.connector.SymbolInfo cacheSymbolDetails(String symbol) {
		if (priceScales.get(symbol) != null && quantityScales.get(symbol) != null) {
			return new cz.amuradon.tralon.agent.connector.SymbolInfo(priceScales.get(symbol));
		}
		
		try {
			String response = mexcClient.exchangeInfo(symbol);
			listener.onExchangeInfo(symbol, response);
			ExchangeInfo exchnageInfo = objectMapper.readValue(response, ExchangeInfo.class);
			SymbolInfo symbolInfo = exchnageInfo.symbols().get(0);
			
			quantityScales.put(symbol, symbolInfo.baseAssetPrecision());
			int priceScale = symbolInfo.quoteAssetPrecision();
			priceScales.put(symbol, priceScale);
			return new cz.amuradon.tralon.agent.connector.SymbolInfo(priceScale);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read JSON.", e);
		}
	}

	@Override
	public NewOrderSymbolBuilder newOrder() {
		return new MexcNewOrderSymbolBuilder();
	}

	private Map<String, Object> signQueryParams(Map<String, Object> params) {
		// It has to be removed for repeated signing of retries to work properly
		params.remove("signature");
    	
		StringJoiner joiner = new StringJoiner("&");
    	for (Entry<String, Object> entry : params.entrySet()) {
			joiner.add(entry.getKey() + "=" + entry.getValue());
		}
    	String signature = HexFormat.of().formatHex(mac.doFinal(joiner.toString().getBytes()));
    	params.put("signature", signature);
    	return params;
    }
	
	@Override
	public String userDataStream() {
		return mexcClient.userDataStream(signQueryParams(param("timestamp", new Date().getTime()))).listenKey();
	}
	
	@Override
	public Ticker[] ticker() {
		return mexcClient.ticker();
	}
	
	@Override
	public void setListener(RestClientListener listener) {
		this.listener = listener;
	}
	
	public class MexcNewOrderSymbolBuilder implements NewOrderSymbolBuilder {

		@Override
		public NewOrderBuilder symbol(String symbol) {
			return new MexcNewOrderBuilder(symbol);
		}
		
	}
	
	public class MexcNewOrderBuilder implements NewOrderBuilder {
    	
    	private static final String TIMESTAMP = "timestamp";
		private Map<String, Object> params = new LinkedHashMap<>();
		private boolean signed = false;
		private String symbol;
		
		public MexcNewOrderBuilder(final String symbol) {
			this.symbol = symbol;
			params.put("symbol", symbol);
		}
    	
		@Override
		public NewOrderBuilder clientOrderId(String clientOrderId) {
			params.put("newClientOrderId", clientOrderId);
			signed = false;
			return this;
		}

		@Override
		public NewOrderBuilder side(Side side) {
    		params.put("side", side.name());
    		signed = false;
    		return this;
    	}

		@Override
    	public NewOrderBuilder type(OrderType type) {
    		params.put("type", type.name());
    		signed = false;
    		return this;
    	}
    	
		@Override
    	public NewOrderBuilder size(BigDecimal quantity) {
			Integer quantityScale = quantityScales.computeIfAbsent(symbol, k -> 2);
			params.put("quantity", quantity.setScale(quantityScale, RoundingMode.HALF_UP).toPlainString());
    		signed = false;
    		return this;
    	}
   
		@Override
    	public NewOrderBuilder price(BigDecimal price) {
			Integer priceScale = priceScales.computeIfAbsent(symbol, k -> 2);
			params.put("price", price.setScale(priceScale, RoundingMode.HALF_UP).toPlainString());
    		signed = false;
    		return this;
    	}
    	
		@Override
    	public NewOrderBuilder timestamp(long timestamp) {
    		params.put(TIMESTAMP, String.valueOf(timestamp));
    		signed = false;
    		return this;
    	}

		@Override
    	public NewOrderBuilder recvWindow(long timestamp) {
    		params.put("recvWindow", String.valueOf(timestamp));
    		signed = false;
    		return this;
    	}

		@Override
    	public NewOrderBuilder signParams() {
    		params = signQueryParams(params);
    		signed = true;
    		return this;
    	}
    	
		@Override
    	public String send() {
    		if (params.get(TIMESTAMP) == null) {
    			params.put(TIMESTAMP, String.valueOf(new Date().getTime()));
    			signed = false;
    		}
    		
			return mexcClient.newOrder(signed ? params : signQueryParams(params)).orderId();
    		
    	}

    }

}
