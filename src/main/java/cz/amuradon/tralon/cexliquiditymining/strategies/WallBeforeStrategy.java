package cz.amuradon.tralon.cexliquiditymining.strategies;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.OrderCreateResponse;

import cz.amuradon.tralon.cexliquiditymining.BalanceHolder;
import cz.amuradon.tralon.cexliquiditymining.Order;
import cz.amuradon.tralon.cexliquiditymining.OrderBook;
import cz.amuradon.tralon.cexliquiditymining.OrderBookUpdate;
import cz.amuradon.tralon.cexliquiditymining.PriceProposal;
import cz.amuradon.tralon.cexliquiditymining.Side;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WallBeforeStrategy implements Strategy {

	private static final String LIMIT = "limit";
	
	private final BigDecimal sideVolumeThreshold;
	
	private final int priceChangeDelayMs;
	
    private final Map<Side, PriceProposal> priceProposals;
    
	private final KucoinRestClient restClient;
    
	private final String symbol;
	
	private final BigDecimal maxBalanceToUse;
	
    private final Map<String, Order> orders;
    
    
    @Inject
    public WallBeforeStrategy(
    		@ConfigProperty(name = "orderBookQuoteVolumeBefore") final int sideVolumeThreshold, 
    		@ConfigProperty(name = "priceChangeDelayMs") final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final KucoinRestClient restClient,
    		@ConfigProperty(name = "baseToken") final String baseToken,
    		@ConfigProperty(name = "quoteToken") final String quoteToken,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final Map<String, Order> orders) {
    	this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.priceProposals = priceProposals;
		this.restClient = restClient;
		symbol = baseToken + "-" + quoteToken;
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
		this.orders = orders;
	}
	
	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		Side side = update.side();
		PriceProposal proposal = priceProposals.get(side);
		
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
					
					cancelOrders(side, proposal.proposedPrice);
				}
			}
		}
	}
	
	private void cancelOrders(Side side, BigDecimal proposedPrice) {
    	Map<String, Order> ordersBeKept = new ConcurrentHashMap<>();
        for (Entry<String, Order> entry: orders.entrySet()) {
        	Order order = entry.getValue();
        	if (side == order.side() && order.price().compareTo(proposedPrice) != 0) {
        		try {
        			Log.infof("Cancelling order %s", order);
					restClient.orderAPI().cancelOrder(order.orderId());
				} catch (IOException e) {
					throw new IllegalStateException("Could not cancel an order " + order.orderId(), e);
				}
        	} else {
        		ordersBeKept.put(entry.getKey(), order);
        	}
        }
        
        synchronized (orders) {
        	orders.clear();
        	orders.putAll(ordersBeKept);
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
    	PriceProposal askProposal = priceProposals.get(Side.SELL);
    	askProposal.currentPrice = askPrice;
    	askProposal.proposedPrice = askPrice;
    	askProposal.timestamp = timestamp;
    			
    	BigDecimal bidPrice = getTargetPriceLevel(orderBook.getBids());
    	PriceProposal bidProposal = priceProposals.get(Side.BUY);
    	bidProposal.currentPrice = bidPrice;
    	bidProposal.proposedPrice = bidPrice;
    	bidProposal.timestamp = timestamp;
    	
    	Log.debugf("First price proposals calculated - ask: %s, bid: %s", askPrice, bidPrice);
		
	}

	// XXX vyplatilo by se rozdelit na base a quote balance update?
	public void onBalanceUpdate(BalanceHolder balanceHolder) {
      	BigDecimal baseBalanceSnapshot = balanceHolder.getBaseBalance();
       	BigDecimal quoteBalanceSnapshot = balanceHolder.getQuoteBalance();
       	
       	BigDecimal askPriceProposal;
       	BigDecimal bidPriceProposal;
       	synchronized (priceProposals) {
       		askPriceProposal = priceProposals.get(Side.SELL).proposedPrice;
       		bidPriceProposal = priceProposals.get(Side.BUY).proposedPrice;
		}
        
		try {
			if (baseBalanceSnapshot.compareTo(BigDecimal.ZERO) > 0) {
				final String clientOrderId = UUID.randomUUID().toString();
				Log.infof("Placing new limit order - clOrdId: %s, side: %s, price: %s, size: %s",
						clientOrderId, Side.SELL, askPriceProposal, baseBalanceSnapshot);
				OrderCreateResponse response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
						.clientOid(clientOrderId)
						.side("sell")
						.symbol(symbol)
						.price(askPriceProposal)
						.size(baseBalanceSnapshot)
						.type(LIMIT)
						.build());
				orders.put(response.getOrderId(), new Order(response.getOrderId(), Side.SELL, baseBalanceSnapshot, askPriceProposal));
			} else {
				Log.info("No new sell orders placed");
			}
			
			BigDecimal balanceQuoteLeftForBids = maxBalanceToUse;
			for (Order order : orders.values()) {
				balanceQuoteLeftForBids = balanceQuoteLeftForBids.subtract(order.price().multiply(order.size()));
			}
			balanceQuoteLeftForBids = balanceQuoteLeftForBids.min(quoteBalanceSnapshot);
			
			BigDecimal size = balanceQuoteLeftForBids.divide(bidPriceProposal, 4, RoundingMode.FLOOR);
			if (size.compareTo(BigDecimal.ZERO) > 0) {
				final String clientOrderId = UUID.randomUUID().toString();
				Log.infof("Placing new limit order - clOrdId: %s, side: %s, price: %s, size: %s",
						clientOrderId, Side.BUY, bidPriceProposal, size);
				OrderCreateResponse response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
						.clientOid(clientOrderId)
						.side("buy")
						.symbol(symbol)
						.price(bidPriceProposal)
						.size(size)
						.type(LIMIT)
						.build());
				orders.put(response.getOrderId(), new Order(response.getOrderId(), Side.BUY, size, bidPriceProposal));
			} else {
				Log.info("No new buy orders placed");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not create orders", e);
		}
    }
}
