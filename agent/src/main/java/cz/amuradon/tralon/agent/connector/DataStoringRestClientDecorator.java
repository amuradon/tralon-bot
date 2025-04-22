package cz.amuradon.tralon.agent.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.model.Order;

public class DataStoringRestClientDecorator implements RestClient {

	private final RestClient delegate;
	
	private final String exchangeName;
	
	private final ExecutorService executorService;
	
	private final String dataDir;
	
	private final ObjectMapper mapper;
	
	public DataStoringRestClientDecorator(final RestClient delegate, final String exchangeName,
			final ExecutorService executorService, final String dataDir) {
		this.delegate = delegate;
		this.exchangeName = exchangeName;
		this.executorService = executorService;
		this.dataDir = dataDir;
		mapper = new ObjectMapper();
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
		LocalDate date = LocalDate.now();
		executorService.execute(() -> {
			try {
				Files.writeString(Paths.get(dataDir, exchangeName,
						date.format(DateTimeFormatter.ofPattern("yyyyMMdd")), symbol, "depth.json"),
						mapper.writeValueAsString(response),
						StandardOpenOption.CREATE);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
		return response;
	}

	@Override
	public SymbolInfo cacheSymbolDetails(String symbol) {
		// TODO write to file
		return delegate.cacheSymbolDetails(symbol);
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
