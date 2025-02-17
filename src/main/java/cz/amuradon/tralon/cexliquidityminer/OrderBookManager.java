package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.response.OrderBookResponse;

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
	
	private final BigDecimal sideVolumeThreshold;
	
	private final int priceChangeDelayMs;
	
	private final ProducerTemplate producer;
	
	private final OrderBook orderBook;
	
    private final Map<Side, PriceProposal> proposals;
    
    private final String symbol;
	
	
	@Inject
	public OrderBookManager(final KucoinRestClient restClient,
			@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
			@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final ProducerTemplate producer, final OrderBook orderBook,
    		final Map<Side, PriceProposal> proposals,
    		@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken) {
		this.restClient = restClient;
		this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.producer = producer;
		this.orderBook = orderBook;
		this.proposals = proposals;
		symbol = baseToken + "-" + quoteToken;
    }
	
	public void processUpdate(OrderBookUpdate update) {
		Log.tracef("Order book update: %s", update);
		final long sequence = update.sequence();
		if (sequence > orderBook.sequence()) {
			orderBook.setSequence(sequence);
			Side side = update.side();
			processUpdateInternal(update, proposals.get(side), side, orderBook.getOrderBookSide(side));
		}
	}
	
	private void processUpdateInternal(OrderBookUpdate update, PriceProposal proposal, Side side,
			Map<BigDecimal, BigDecimal> orderBookSide) {
		
		if (update.size().compareTo(BigDecimal.ZERO) == 0) {
			orderBookSide.remove(update.price());
		} else {
			orderBookSide.put(update.price(), update.size());
		}
		
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
					
					producer.sendBodyAndHeader(MyRouteBuilder.SEDA_CANCEL_ORDERS,
							proposal.proposedPrice, "Side", side);
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
	
	public void createLocalOrderBook() {
		try {
	    	OrderBookResponse orderBookResponse = restClient.orderBookAPI().getAllLevel2OrderBook(symbol);
	    	Log.infof("Order Book response: seq %s\nAsks:\n%s\nBids:\n%s", orderBookResponse.getSequence(),
	    			orderBookResponse.getAsks(), orderBookResponse.getBids());
	    	orderBook.setSequence(Long.parseLong(orderBookResponse.getSequence()));
				setOrderBookSide(orderBookResponse.getAsks(), orderBook.getAsks());
	    	setOrderBookSide(orderBookResponse.getBids(), orderBook.getBids());
	    	
	    	Log.debugf("Order book created: %s", orderBook);
	    	
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
	    	
		} catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	private void setOrderBookSide(List<List<String>> list, final Map<BigDecimal, BigDecimal> map) {
    	for (List<String> element : list) {
			map.put(new BigDecimal(element.get(0)), new BigDecimal(element.get(1)));
		}
    }

}
