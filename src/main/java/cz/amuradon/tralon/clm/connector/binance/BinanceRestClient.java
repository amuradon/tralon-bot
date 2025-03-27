package cz.amuradon.tralon.clm.connector.binance;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.binance.connector.client.SpotClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import cz.amuradon.tralon.clm.OrderType;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.model.Order;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProfile("binance")
public class BinanceRestClient implements RestClient {

	private final SpotClient spotClient;
	
	private final ObjectMapper mapper;
	
	@Inject
	public BinanceRestClient(SpotClient spotClient) {
		this.spotClient = spotClient;
		mapper = new ObjectMapper();
	}
	
	@Override
	public NewOrderBuilder newOrder() {
		return new BinanceNewOrderBuilder();
	}

	@Override
	public void cancelOrder(Order order) {
		spotClient.createTrade()
			.cancelOrder(ImmutableMap.<String, Object>of("symbol", order.symbol(), "orderId", order.orderId()));
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		final String response = spotClient.createTrade().getOpenOrders(ImmutableMap.<String, Object>of("symbol", symbol));
		try {
			return mapper.readValue(response, new TypeReference<List<BinanceOrder>>() { })
					.stream().collect(Collectors.toMap(o -> o.orderId(), o -> o));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read open orders.", e);
		}
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		final String response = spotClient.createTrade().account(Collections.emptyMap());
		try {
			return mapper.readValue(response, BinanceAccountInformation.class).balances();
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not read account information.", e);
		}
	}

	public final class BinanceNewOrderBuilder implements NewOrderBuilder {

		private final Map<String, Object> parameters = new HashMap<>();
		
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
			return this;
		}

		@Override
		public NewOrderBuilder price(BigDecimal price) {
			parameters.put("price", price);
			return this;
		}

		@Override
		public NewOrderBuilder size(BigDecimal size) {
			parameters.put("quantity", size);
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
			return spotClient.createTrade().newOrder(parameters);
		}
		
		
	}
}
