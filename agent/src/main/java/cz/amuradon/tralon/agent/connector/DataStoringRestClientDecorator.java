package cz.amuradon.tralon.agent.connector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import cz.amuradon.tralon.agent.model.Order;

public class DataStoringRestClientDecorator extends DataStoringBase implements RestClient {

	private final RestClient delegate;
	
	public DataStoringRestClientDecorator(final RestClient delegate,
			final ExecutorService executorService, final Path dataDir) {
		super(executorService, dataDir);
		this.delegate = delegate;
	}
	
	@Override
	public void cancelOrder(String orderId, String symbol) {
		delegate.cancelOrder(orderId, symbol);
	}

	@Override
	public Map<String, Order> listOrders(String symbol) {
		return delegate.listOrders(symbol);
	}

	@Override
	public List<? extends AccountBalance> listBalances() {
		return delegate.listBalances();
	}

	@Override
	public OrderBookResponse orderBook(String symbol) {
		OrderBookResponse response = delegate.orderBook(symbol);
		writeToFile(symbol, response, "depth.json");
		return response;
	}
	
	@Override
	public SymbolInfo cacheSymbolDetails(String symbol) {
		SymbolInfo response = delegate.cacheSymbolDetails(symbol);
		writeToFile(symbol, response, "exchangeInfo.json");
		return response;
	}

	@Override
	public NewOrderBuilder newOrder() {
		return delegate.newOrder();
	}

	@Override
	public String userDataStream() {
		return delegate.userDataStream();
	}
	
	@Override
	public String toString() {
		return String.format("Data storing: %s", delegate.toString());
	}
}
