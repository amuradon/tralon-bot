package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
	
	private final BigDecimal sideVolumeThreshold;
	
	private final int priceChangeDelayMs;
	
	private final ProducerTemplate producer;
	
	private final OrderBook orderBook;
	
    private final Map<Side, PriceLevelProposal> proposals;
	
	
	@Inject
	public OrderBookManager(
			@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
			@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final ProducerTemplate producer, final OrderBook orderBook) {
		this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.producer = producer;
		this.orderBook = orderBook;
		proposals = new ConcurrentHashMap<>();
		proposals.put(Side.BUY, new PriceLevelProposal());
		proposals.put(Side.SELL, new PriceLevelProposal());
    }
	
	public void processUpdate(OrderBookUpdate update) {
		final long sequence = update.sequence();
		if (sequence > orderBook.sequence()) {
			orderBook.setSequence(sequence);
			Side side = update.side();
			processUpdateInternal(update, proposals.get(side), side, orderBook.getOrderBookSide(side));
		}
	}
	
	private void processUpdateInternal(OrderBookUpdate update, PriceLevelProposal proposal, Side side,
			Map<BigDecimal, BigDecimal> orderBookSide) {
		
		if (update.size().compareTo(BigDecimal.ZERO) == 0) {
			orderBookSide.remove(update.price());
		} else {
			orderBookSide.put(update.price(), update.size());
		}
		
		
		// TODO if the update's price is farther then target price level, skip the processing below
		long timestamp = update.time();
		
		BigDecimal targetPrice = getTargetPriceLevel(orderBook.getAsks());
		
		Log.debugf("Target ask price: %s", targetPrice);
		
		// TODO do as little computation as possible, if there is no change, no computation
		if (proposal.currentPrice.compareTo(targetPrice) != 0) {
			if (proposal.proposedPrice.compareTo(targetPrice) != 0) {
				proposal.proposedPrice = targetPrice;
				proposal.timestamp = timestamp;
			}
		} else if (proposal.proposedPrice.compareTo(proposal.currentPrice) != 0) {
			proposal.proposedPrice = proposal.currentPrice;
			proposal.timestamp = Long.MAX_VALUE - priceChangeDelayMs;
		}
		
		if (timestamp >= proposal.timestamp + priceChangeDelayMs) {
			
			proposal.currentPrice = proposal.proposedPrice;
			proposal.timestamp = Long.MAX_VALUE - priceChangeDelayMs;
			
			producer.sendBodyAndHeader(MyRouteBuilder.SEDA_PROCESS_ORDER_CHANGES,
					proposal.proposedPrice, "Side", side);
		}
	}
	
	 private BigDecimal getTargetPriceLevel(Map<BigDecimal, BigDecimal> aggregatedOrders) {
    	BigDecimal volume = BigDecimal.ZERO;
    	BigDecimal price = BigDecimal.ZERO;
    	for (Entry<BigDecimal, BigDecimal> entry : aggregatedOrders.entrySet()) {
    		price = entry.getKey();
			volume = volume.add(price.multiply(entry.getValue()));
			if (volume.compareTo(sideVolumeThreshold) >= 0) {
				break;
			}
		}
    	
    	return price;
    }
	 
	private static final class PriceLevelProposal {
		private BigDecimal currentPrice = BigDecimal.ZERO;
	    
	    private BigDecimal proposedPrice = BigDecimal.ZERO;
	    
	    private long timestamp;
	}
}
