package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cz.amuradon.tralon.clm.OrderBook;
import cz.amuradon.tralon.clm.OrderType;
import cz.amuradon.tralon.clm.PriceProposal;
import cz.amuradon.tralon.clm.Side;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.model.Order;
import cz.amuradon.tralon.clm.model.OrderImpl;
import io.quarkus.logging.Log;

public abstract class AbstractStrategy implements Strategy {

	private final RestClient restClient;
    
	private final String symbol;
	
    private final Map<String, Order> orders;
    
	private final int priceChangeDelayMs;
	
    private final Map<Side, PriceProposal> priceProposals;
    
	private final BigDecimal maxBalanceToUse;
	
	private final ScheduledExecutorService scheduler;

	private final Map<Side, ScheduledFuture<?>> cancelOrdersTasks;
    
    public AbstractStrategy(
    		final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final RestClient restClient,
    		final String symbol,
    		final int maxBalanceToUse,
    		final Map<String, Order> orders,
    		final ScheduledExecutorService scheduler) {
		this.restClient = restClient;
		this.symbol = symbol;
		this.orders = orders;
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.priceProposals = priceProposals;
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
		this.scheduler = scheduler;
		cancelOrdersTasks = new HashMap<>();
	}
	
    @Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		Side side = update.side();
		PriceProposal proposal = priceProposals.get(side);
		// TODO timestamp z update -> nezpracovat starsi update?
		// Serves as monitor for all things on the same side
		synchronized (proposal) {
			if (!side.isPriceOutOfRange(update.price(), proposal.currentPrice)) {
				
				BigDecimal targetPrice = getTargetPriceLevel(orderBookSide);
				
				Log.debugf("Target %s price: %s, proposal: %s", side, targetPrice, proposal);
				
				// FIXME scheduling nefunguje
				proposal.proposedPrice = targetPrice;
				ScheduledFuture<?> cancelOrdersTask = cancelOrdersTasks.get(side);
				if (proposal.currentPrice.compareTo(targetPrice) != 0) {
					if (cancelOrdersTask == null) {
						Log.debugf("Scheduling job for %s", side);
						cancelOrdersTask = scheduler.schedule(() -> cancelOrders(side),
							priceChangeDelayMs, TimeUnit.MILLISECONDS);
						cancelOrdersTasks.put(side, cancelOrdersTask);
					}
				} else {
					if (cancelOrdersTask != null) {
						Log.debugf("Cancelling job for %s", side);
						cancelOrdersTask.cancel(false);
						cancelOrdersTasks.remove(side);
					}
				}
			}
		}
	}
	
	abstract BigDecimal getTargetPriceLevel(Map<BigDecimal, BigDecimal> aggregatedOrders);

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

	// FIXME Na Kucoin nechodi balance updates spolehlive!
	@Override
	public void onBaseBalanceUpdate(BigDecimal balance) {
       	PriceProposal priceProposal = priceProposals.get(Side.SELL);
       	
       	synchronized (priceProposal) {
			BigDecimal askPriceProposal = priceProposal.proposedPrice;
			priceProposal.currentPrice = askPriceProposal;
	        
			if (balance.compareTo(BigDecimal.ZERO) > 0) {
				placeOrder(Side.SELL, askPriceProposal, balance);
			} else {
				Log.info("No new sell orders placed");
			}
       	}
    }

	@Override
	public void onQuoteBalanceUpdate(BigDecimal balance) {
		PriceProposal priceProposal = priceProposals.get(Side.BUY);
		
		synchronized (priceProposal) {
			BigDecimal bidPriceProposal = priceProposal.proposedPrice;
			priceProposal.currentPrice = bidPriceProposal;
			
			BigDecimal balanceQuoteLeftForBids = maxBalanceToUse;
			for (Order order : orders.values()) {
				balanceQuoteLeftForBids = balanceQuoteLeftForBids.subtract(order.price().multiply(order.size()));
			}
			balanceQuoteLeftForBids = balanceQuoteLeftForBids.min(balance);
			
			// TODO get correct scale from exchange
			BigDecimal size = balanceQuoteLeftForBids.divide(bidPriceProposal, 4, RoundingMode.FLOOR);
			if (size.compareTo(BigDecimal.ZERO) > 0) {
				placeOrder(Side.BUY, bidPriceProposal, size);
			} else {
				Log.info("No new buy orders placed");
			}
		}
	}
    
	String placeOrder(Side side, BigDecimal price, BigDecimal size) {
		final String clientOrderId = UUID.randomUUID().toString();
		Log.infof("Placing new limit order - clOrdId: %s, side: %s, price: %s, size: %s",
				clientOrderId, side, price, size);
		String orderId = restClient.newOrder()
				.clientOrderId(clientOrderId)
				.side(side)
				.symbol(symbol)
				.price(price)
				.size(size)
				.type(OrderType.LIMIT)
				.send();
		orders.put(orderId, new OrderImpl(orderId, symbol, Side.SELL, size, price));
		return clientOrderId;
	}
	
	void cancelOrders(Side side) {
		BigDecimal proposedPrice = priceProposals.get(side).proposedPrice;
    	Map<String, Order> ordersBeKept = new ConcurrentHashMap<>();
        for (Entry<String, Order> entry: orders.entrySet()) {
        	Order order = entry.getValue();
        	if (side == order.side() && order.price().compareTo(proposedPrice) != 0) {
    			Log.infof("Cancelling order %s", order);
				restClient.cancelOrder(order);
        	} else {
        		ordersBeKept.put(entry.getKey(), order);
        	}
        }
        
        synchronized (orders) {
        	orders.clear();
        	orders.putAll(ordersBeKept);
		}
    }
}
