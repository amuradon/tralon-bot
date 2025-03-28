package cz.amuradon.tralon.clm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.clm.connector.OrderBookResponse;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.strategies.Strategy;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named(OrderBookManager.BEAN_NAME)
@RegisterForReflection
public class OrderBookManager {

	public static final String BEAN_NAME = "orderBookManager";
	
	private final RestClient restClient;
	
	private final OrderBook orderBook;
	
    private final String symbol;
    
    private final Strategy strategy; 
    
    private Consumer<OrderBookUpdate> processor;
    
    private List<OrderBookUpdate> orderBookUpdates;
    
	
	@Inject
	public OrderBookManager(final RestClient restClient,
			final OrderBook orderBook,
			@Named(BeanConfig.SYMBOL) final String symbol,
    		final Strategy strategy) {
		this.restClient = restClient;
		this.orderBook = orderBook;
		this.strategy = strategy;
		this.symbol = symbol;
		orderBookUpdates = new ArrayList<>(50);
		processor = u -> {
			synchronized (orderBookUpdates) {
				orderBookUpdates.add(u);
			}
		};
    }
	
	public void processUpdate(OrderBookUpdate update) {
		processor.accept(update);
	}
	
	private void updateOrderBook(OrderBookUpdate update) {
		Log.tracef("Order book update: %s", update);
		
		if (update.setSequenceIfShouldBeProcessed(orderBook)) {
			Map<BigDecimal, BigDecimal> orderBookSide = orderBook.getOrderBookSide(update.side());
			
			if (update.size().compareTo(BigDecimal.ZERO) == 0) {
				orderBookSide.remove(update.price());
			} else {
				orderBookSide.put(update.price(), update.size());
			}
		}
	}
	
	public void createLocalOrderBook() {
    	OrderBookResponse orderBookResponse = restClient.getOrderBook(symbol);
    	Log.infof("Order Book response: seq %s\nAsks:\n%s\nBids:\n%s", orderBookResponse.sequence(),
    			orderBookResponse.asks(), orderBookResponse.bids());
    	orderBook.setSequence(orderBookResponse.sequence());
		orderBook.setAsks(orderBookResponse.asks());
    	orderBook.setBids(orderBookResponse.bids());
    	
    	Log.debugf("Order book created: %s", orderBook);
    	
    	synchronized (orderBookUpdates) {
    		processor = u -> {
    			updateOrderBook(u);
    			// located here to avoid calling strategy when building local order book
    			strategy.onOrderBookUpdate(u, orderBook.getOrderBookSide(u.side()));
    		};
			for (OrderBookUpdate orderBookUpdate : orderBookUpdates) {
				updateOrderBook(orderBookUpdate);
			}
			orderBookUpdates.clear();
			strategy.computeInitialPrices(orderBook);
		}
	    	
	}
	
}
