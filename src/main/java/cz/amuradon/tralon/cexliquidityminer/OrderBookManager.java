package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;

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
	
	private BigDecimal currentBidPriceLevel;
    
    private BigDecimal currentAskPriceLevel;
    
    private BigDecimal bidPriceLevelProposal;
    
    private BigDecimal askPriceLevelProposal;
    
    private long askPriceLevelProposalTimestamp;
    
    private long bidPriceLevelProposalTimestamp;
	
	
	@Inject
	public OrderBookManager(
			@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
			@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final ProducerTemplate producer, final OrderBook orderBook) {
		this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.producer = producer;
		this.orderBook = orderBook;
		currentBidPriceLevel = BigDecimal.ZERO;
		currentAskPriceLevel = BigDecimal.ZERO;
		bidPriceLevelProposal = BigDecimal.ZERO;
		askPriceLevelProposal = BigDecimal.ZERO;
    }
	
	public void processUpdate(OrderBookUpdate update) {
		final long sequence = update.sequence();
		if (sequence > orderBook.sequence()) {
			if (update.side() == Side.BUY) {
				if (update.size().compareTo(BigDecimal.ZERO) == 0) {
					orderBook.getBids().remove(update.price());
				} else {
					orderBook.getBids().put(update.price(), update.size());
				}
			} else if (update.side() == Side.SELL) {
				if (update.size().compareTo(BigDecimal.ZERO) == 0) {
					orderBook.getAsks().remove(update.price());
				} else {
					orderBook.getAsks().put(update.price(), update.size());
				}
			}
			orderBook.setSequence(sequence);
		
	        long timestamp = update.time();
	
	        BigDecimal askPriceLevel = getTargetPriceLevel(orderBook.getAsks());
	        BigDecimal bidPriceLevel = getTargetPriceLevel(orderBook.getBids());
	        
	        Log.debugf("Target ask price: %s", askPriceLevel);
	        Log.debugf("Target bid price: %s", bidPriceLevel);
	        
	        // TODO do as little computation as possible, if there is no change, no computation
	        if (currentAskPriceLevel.compareTo(askPriceLevel) != 0) {
	        	if (askPriceLevelProposal.compareTo(askPriceLevel) != 0) {
	        		askPriceLevelProposal = askPriceLevel;
	        		askPriceLevelProposalTimestamp = timestamp;
	        	}
	        } else if (askPriceLevelProposal.compareTo(currentAskPriceLevel) != 0) {
	        	askPriceLevelProposal = currentAskPriceLevel;
	        	askPriceLevelProposalTimestamp = Long.MAX_VALUE - priceChangeDelayMs;
	        }
	        
	        if (currentBidPriceLevel.compareTo(bidPriceLevel) != 0) {
	        	bidPriceLevelProposal = bidPriceLevel;
	        	bidPriceLevelProposalTimestamp = timestamp;
	        } else if (bidPriceLevelProposal.compareTo(currentBidPriceLevel) != 0) {
	        	bidPriceLevelProposal = currentBidPriceLevel;
	        	bidPriceLevelProposalTimestamp = Long.MAX_VALUE - priceChangeDelayMs;
	        }
	        
	        if (timestamp >= askPriceLevelProposalTimestamp + priceChangeDelayMs ||
	        		timestamp >= bidPriceLevelProposalTimestamp + priceChangeDelayMs) {
	
	        	currentAskPriceLevel = askPriceLevelProposal;
	        	currentBidPriceLevel = bidPriceLevelProposal;
	        	askPriceLevelProposalTimestamp = Long.MAX_VALUE - priceChangeDelayMs;
	        	bidPriceLevelProposalTimestamp = Long.MAX_VALUE - priceChangeDelayMs;
	        	
	        	producer.sendBody(MyRouteBuilder.SEDA_PROCESS_ORDER_CHANGES,
	        			new PriceLevelProposalHolder(askPriceLevelProposal, bidPriceLevelProposal));
	        }
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
}
