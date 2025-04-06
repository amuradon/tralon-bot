package cz.amuradon.tralon.clm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import cz.amuradon.tralon.clm.connector.OrderBookResponse;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;
import cz.amuradon.tralon.clm.connector.RestClient;
import io.quarkus.logging.Log;

public class OrderBookManager {

	private final RestClient restClient;
	
	private final OrderBook orderBook;
	
    private Consumer<OrderBookUpdate> processor;
    
    private List<OrderBookUpdate> orderBookUpdates;
    
	public OrderBookManager(final RestClient restClient,
			final OrderBook orderBook) {
		this.restClient = restClient;
		this.orderBook = orderBook;
		orderBookUpdates = new ArrayList<>(50);
		processor = u -> {
			synchronized (orderBookUpdates) {
				orderBookUpdates.add(u);
			}
		};
    }
	
	public OrderBook processUpdate(OrderBookUpdate update) {
		processor.accept(update);
		return orderBook;
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
	
	public OrderBook createLocalOrderBook(String symbol) {
    	OrderBookResponse orderBookResponse = restClient.orderBook(symbol);
    	Log.infof("Order Book response: seq %s\nAsks:\n%s\nBids:\n%s", orderBookResponse.sequence(),
    			orderBookResponse.asks(), orderBookResponse.bids());
    	orderBook.setSequence(orderBookResponse.sequence());
		orderBook.setAsks(orderBookResponse.asks());
    	orderBook.setBids(orderBookResponse.bids());
    	
    	Log.debugf("Order book created: %s", orderBook);
    	
    	synchronized (orderBookUpdates) {
    		processor = u -> {
    			updateOrderBook(u);
    		};
			for (OrderBookUpdate orderBookUpdate : orderBookUpdates) {
				updateOrderBook(orderBookUpdate);
			}
			orderBookUpdates.clear();
		}
    	return orderBook;
	    	
	}
	
}
