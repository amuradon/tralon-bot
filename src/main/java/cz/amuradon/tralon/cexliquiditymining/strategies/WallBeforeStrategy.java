package cz.amuradon.tralon.cexliquiditymining.strategies;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.cexliquiditymining.CancelOrders;
import cz.amuradon.tralon.cexliquiditymining.OrderBook;
import cz.amuradon.tralon.cexliquiditymining.OrderBookUpdate;
import cz.amuradon.tralon.cexliquiditymining.PriceProposal;
import cz.amuradon.tralon.cexliquiditymining.Side;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WallBeforeStrategy implements Strategy {

	private final BigDecimal sideVolumeThreshold;
	
	private final int priceChangeDelayMs;
	
	private final CancelOrders cancelOrders;
	
    private final Map<Side, PriceProposal> proposals;
    
    @Inject
    public WallBeforeStrategy(
    		@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
    		@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
			final CancelOrders cancelOrders,
    		final Map<Side, PriceProposal> proposals) {
    	this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.cancelOrders = cancelOrders;
		this.proposals = proposals;
	}
	
	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		Side side = update.side();
		PriceProposal proposal = proposals.get(side);
		
		// TODO do as little computation as possible, if there is no change, no computation
		synchronized (proposal) {
			if (!side.isPriceOutOfRange(update.price(), proposal.currentPrice)) {
				long timestamp = update.time();
				
				
				BigDecimal targetPrice = getTargetPriceLevel(orderBookSide);
				
				Log.debugf("Target %s price: %s", side, targetPrice);
				
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
					
					cancelOrders.processOrderChanges(side, proposal.proposedPrice);
				}
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

	@Override
	public void computeInitialPrices(OrderBook orderBook) {
		long timestamp = new Date().getTime();
    	BigDecimal askPrice = getTargetPriceLevel(orderBook.getAsks());
    	PriceProposal askProposal = proposals.get(Side.SELL);
    	askProposal.currentPrice = askPrice;
    	askProposal.proposedPrice = askPrice;
    	askProposal.timestamp = timestamp;
    			
    	BigDecimal bidPrice = getTargetPriceLevel(orderBook.getBids());
    	PriceProposal bidProposal = proposals.get(Side.BUY);
    	bidProposal.currentPrice = bidPrice;
    	bidProposal.proposedPrice = bidPrice;
    	bidProposal.timestamp = timestamp;
    	
    	Log.debugf("First price proposals calculated - ask: %s, bid: %s", askPrice, bidPrice);
		
	}

}
