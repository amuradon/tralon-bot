package cz.amuradon.tralon.clm.connector.mexc;

import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.clm.OrderType;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.OrderBookResponse;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.model.Order;
import jakarta.inject.Inject;

public class MexcRestClientAdapter implements RestClient {

	private static final String HMAC_SHA256 = "HmacSHA256";
	
	private final MexcClient mexcClient;
	
	private Mac mac;
	
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
	}
	
	@Override
	public void cancelOrder(Order order) {
		Map<String, String> params = new LinkedHashMap<>();
    	params.put("symbol", order.symbol());
    	params.put("orderId", order.orderId());
    	params.put("timestamp", String.valueOf(new Date().getTime()));
    	
    	// XXX should return OrderResponse?
    	mexcClient.cancelOrder(signQueryParams(params));
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderBookResponse orderBook(String symbol) {
		return mexcClient.orderBook(symbol);
	}

	@Override
	public void cacheSymbolDetails(String symbol) {
		// TODO Auto-generated method stub
	}

	@Override
	public NewOrderBuilder newOrder() {
		// TODO Auto-generated method stub
		return null;
	}

	public Map<String, String> signQueryParams(Map<String, String> params) {
    	StringJoiner joiner = new StringJoiner("&");
    	for (Entry<String, String> entry : params.entrySet()) {
			joiner.add(entry.getKey() + "=" + entry.getValue());
		}
    	String signature = HexFormat.of().formatHex(mac.doFinal(joiner.toString().getBytes()));
    	params.put("signature", signature);
    	return params;
    }
	
	public class MexcNewOrderRequestBuilder implements NewOrderBuilder {
    	
    	private static final String TIMESTAMP = "timestamp";
		private Map<String, String> params = new LinkedHashMap<>();
		private boolean signed = false;
    	
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
    		signed = false;
    		return this;
    	}

    	public NewOrderBuilder type(OrderType type) {
    		params.put("type", type.name());
    		signed = false;
    		return this;
    	}
    	
    	public NewOrderBuilder size(BigDecimal size) {
    		params.put("quantity", size.toPlainString());
    		signed = false;
    		return this;
    	}
   
    	public NewOrderBuilder price(BigDecimal price) {
    		params.put("price", price.toPlainString());
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
    		if (params.get(TIMESTAMP) == null) {
    			params.put(TIMESTAMP, String.valueOf(new Date().getTime()));
    			signed = false;
    		}
    		
    		// XXX should return OrderResponse?
    		return mexcClient.newOrder(signed ? params : signQueryParams(params)).orderId();
    	}

    }
}
