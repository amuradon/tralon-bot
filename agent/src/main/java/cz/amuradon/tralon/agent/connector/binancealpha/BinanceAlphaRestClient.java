package cz.amuradon.tralon.agent.connector.binancealpha;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;

import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.RestClientListener;
import cz.amuradon.tralon.agent.connector.SymbolInfo;
import cz.amuradon.tralon.agent.connector.Ticker;
import cz.amuradon.tralon.agent.model.Order;
import jakarta.enterprise.context.Dependent;

@Dependent
@BinanceAlpha
@RestClientFactory // Required for proper usage with Instance
public class BinanceAlphaRestClient implements RestClient {

	private final BinanceAlphaClient client;
	
	public BinanceAlphaRestClient(
			@org.eclipse.microprofile.rest.client.inject.RestClient final BinanceAlphaClient client) {
		this.client = client;
	}
	
	@Override
	public void cancelOrder(String orderId, String symbol) {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public OrderBookResponse orderBook(String symbol) {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public SymbolInfo cacheSymbolDetails(String symbol) {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public NewOrderSymbolBuilder newOrder() {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public String userDataStream() {
		throw new NotImplementedException("Not implemented");
	}

	@Override
	public Ticker[] ticker() {
		return client.ticker().data();
	}

	@Override
	public void setListener(RestClientListener listener) {
		throw new NotImplementedException("Not implemented");
	}

}
