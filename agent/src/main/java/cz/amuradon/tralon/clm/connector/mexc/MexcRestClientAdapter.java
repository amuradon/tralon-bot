package cz.amuradon.tralon.clm.connector.mexc;

import static cz.amuradon.tralon.clm.connector.RequestUtils.param;

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

import cz.amuradon.tralon.clm.OrderType;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.OrderBookResponse;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.RestClientFactory;
import cz.amuradon.tralon.clm.model.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Mexc
@RestClientFactory // Required for proper usage with Instance
public class MexcRestClientAdapter implements RestClient {

	private static final String HMAC_SHA256 = "HmacSHA256";
	
	private final MexcClient mexcClient;
	
	private Mac mac;
	
	private final Map<String, Integer> quantityScales;
	private final Map<String, Integer> priceScales;
	
	@Inject
	public MexcRestClientAdapter(@ConfigProperty(name = "mexc.secretKey") final String secretKey,
			@org.eclipse.microprofile.rest.client.inject.RestClient final MexcClient mexcClient) {
		this.mexcClient = mexcClient;
		try {
			mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secretKey.getBytes(), HMAC_SHA256));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Could not setup encoder", e);
		}
		quantityScales = new ConcurrentHashMap<>();
		priceScales = new ConcurrentHashMap<>();
	}
	
	@Override
	public void cancelOrder(Order order) {
    	mexcClient.cancelOrder(
    			param("timestamp", new Date().getTime())
    			.param("symbol", order.symbol())
    			.param("orderId", order.orderId()));
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
		return mexcClient.orderBook(symbol);
	}

	@Override
	public void cacheSymbolDetails(String symbol) {
		if (priceScales.get(symbol) != null && quantityScales.get(symbol) != null) {
			return;
		}
		
		ExchangeInfo exchnageInfo = mexcClient.exchangeInfo(symbol);
		SymbolInfo symbolInfo = exchnageInfo.symbols().get(0);
		
		quantityScales.put(symbol, symbolInfo.baseSizePrecision().stripTrailingZeros().scale());
		priceScales.put(symbol, symbolInfo.quoteAssetPrecision());
	}

	@Override
	public NewOrderBuilder newOrder() {
		return new MexcNewOrderRequestBuilder();
	}

	public Map<String, Object> signQueryParams(Map<String, Object> params) {
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
	
	public class MexcNewOrderRequestBuilder implements NewOrderBuilder {
    	
    	private static final String TIMESTAMP = "timestamp";
		private Map<String, Object> params = new LinkedHashMap<>();
		private boolean signed = false;
		private String symbol;
		private BigDecimal quantity;
		private BigDecimal price;
    	
		public NewOrderBuilder clientOrderId(String clientOrderId) {
			params.put("newClientOrderId", clientOrderId);
			signed = false;
			return this;
		}

		public NewOrderBuilder side(Side side) {
    		params.put("side", side.name());
    		signed = false;
    		return this;
    	}

    	public NewOrderBuilder symbol(String symbol) {
    		params.put("symbol", symbol);
    		this.symbol = symbol;
    		signed = false;
    		return this;
    	}

    	public NewOrderBuilder type(OrderType type) {
    		params.put("type", type.name());
    		signed = false;
    		return this;
    	}
    	
    	public NewOrderBuilder size(BigDecimal size) {
    		this.quantity = size;
    		signed = false;
    		return this;
    	}
   
    	public NewOrderBuilder price(BigDecimal price) {
    		this.price = price;
    		signed = false;
    		return this;
    	}
    	
    	public NewOrderBuilder timestamp(long timestamp) {
    		params.put(TIMESTAMP, String.valueOf(timestamp));
    		signed = false;
    		return this;
    	}

    	public NewOrderBuilder recvWindow(long timestamp) {
    		params.put("recvWindow", String.valueOf(timestamp));
    		signed = false;
    		return this;
    	}

    	public NewOrderBuilder signParams() {
    		params = signQueryParams(params);
    		signed = true;
    		return this;
    	}
    	
    	public String send() {
    		if (symbol == null) {
				throw new IllegalArgumentException("Could not send order - symbol is missing.");
			}
			
			Integer quantityScale = quantityScales.get(symbol);
			params.put("quantity", quantity.setScale(quantityScale, RoundingMode.HALF_UP).toPlainString());

			Integer priceScale = priceScales.get(symbol);
			params.put("price", price.setScale(priceScale, RoundingMode.HALF_UP).toPlainString());
			
    		if (params.get(TIMESTAMP) == null) {
    			params.put(TIMESTAMP, String.valueOf(new Date().getTime()));
    			signed = false;
    		}
    		
    		return mexcClient.newOrder(signed ? params : signQueryParams(params)).orderId();
    	}

    }
}
