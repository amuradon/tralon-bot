package cz.amuradon.tralon.clm;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.response.OrderBookResponse;

import cz.amuradon.tralon.clm.connector.OrderBookUpdate;
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
	
	private final KucoinRestClient restClient;
	
	private final OrderBook orderBook;
	
    private final String symbol;
    
    private final Strategy strategy; 
    
    private Consumer<OrderBookUpdate> processor;
    
    private List<OrderBookUpdate> orderBookUpdates;
    
	
	@Inject
	public OrderBookManager(final KucoinRestClient restClient,
			final OrderBook orderBook,
    		@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken,
    		@Named(BeanConfig.STRATEGY) final Strategy strategy) {
		this.restClient = restClient;
		this.orderBook = orderBook;
		this.strategy = strategy;
		symbol = baseToken + "-" + quoteToken;
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
		final long sequence = update.sequence();
		
		if (sequence <= orderBook.sequence()) {
			return;
		}
		
		orderBook.setSequence(sequence);
		Map<BigDecimal, BigDecimal> orderBookSide = orderBook.getOrderBookSide(update.side());
		
		if (update.size().compareTo(BigDecimal.ZERO) == 0) {
			orderBookSide.remove(update.price());
		} else {
			orderBookSide.put(update.price(), update.size());
		}
	}
	
	public void createLocalOrderBook() {
		try {
	    	OrderBookResponse orderBookResponse = restClient.orderBookAPI().getAllLevel2OrderBook(symbol);
	    	Log.infof("Order Book response: seq %s\nAsks:\n%s\nBids:\n%s", orderBookResponse.getSequence(),
	    			orderBookResponse.getAsks(), orderBookResponse.getBids());
	    	orderBook.setSequence(Long.parseLong(orderBookResponse.getSequence()));
				setOrderBookSide(orderBookResponse.getAsks(), orderBook.getAsks());
	    	setOrderBookSide(orderBookResponse.getBids(), orderBook.getBids());
	    	
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
	    	
		} catch (IOException e) {
			throw new IllegalStateException("Could not create local order book.", e);
		}
	}
	
	private void setOrderBookSide(List<List<String>> list, final Map<BigDecimal, BigDecimal> map) {
    	for (List<String> element : list) {
			map.put(new BigDecimal(element.get(0)), new BigDecimal(element.get(1)));
		}
    }

}
