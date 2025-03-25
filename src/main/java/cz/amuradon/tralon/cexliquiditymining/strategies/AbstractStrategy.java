package cz.amuradon.tralon.cexliquiditymining.strategies;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.OrderCreateResponse;

import cz.amuradon.tralon.cexliquiditymining.Order;
import cz.amuradon.tralon.cexliquiditymining.OrderBook;
import cz.amuradon.tralon.cexliquiditymining.OrderBookUpdate;
import cz.amuradon.tralon.cexliquiditymining.PriceProposal;
import cz.amuradon.tralon.cexliquiditymining.Side;
import io.quarkus.logging.Log;

public abstract class AbstractStrategy implements Strategy {

	private static final String LIMIT = "limit";
	
	private final KucoinRestClient restClient;
    
	private final String symbol;
	
    private final Map<String, Order> orders;
    
	private final int priceChangeDelayMs;
	
    private final Map<Side, PriceProposal> priceProposals;
    
	private final BigDecimal maxBalanceToUse;
    
    public AbstractStrategy(
    		final int priceChangeDelayMs,
    		final Map<Side, PriceProposal> priceProposals,
    		final KucoinRestClient restClient,
    		final String baseToken,
    		final String quoteToken,
    		final int maxBalanceToUse,
    		final Map<String, Order> orders) {
		this.restClient = restClient;
		this.symbol = baseToken + "-" + quoteToken;
		this.orders = orders;
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.priceProposals = priceProposals;
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
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
				// TODO priceChangeDelayMs nefunguje spravne, reaguje az na novy update, nutno pouzit casovac
				if (timestamp >= proposal.timestamp + priceChangeDelayMs) {
					
					proposal.currentPrice = proposal.proposedPrice;
					proposal.timestamp = Long.MAX_VALUE - priceChangeDelayMs;
					
					cancelOrders(side, proposal.proposedPrice);
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

	// FIXME udelal jsem SELL order, ale neprisel mi VERSE balance update, pri USDT balance update se pokusil zalozit
	// zase novy SELL order a to spadlo na nedostatek balance -> cist aktualni balance pres REST API
	// FIXME nekde je neco spatne, dostanu se do chvile, kdy se zrusi ordery a nove se nezalozi
	@Override
	public void onBaseBalanceUpdate(BigDecimal balance) {
       	BigDecimal askPriceProposal;
       	synchronized (priceProposals) {
       		askPriceProposal = priceProposals.get(Side.SELL).proposedPrice;
		}
        
		if (balance.compareTo(BigDecimal.ZERO) > 0) {
			placeOrder(Side.SELL, askPriceProposal, balance);
		} else {
			Log.info("No new sell orders placed");
		}
    }

	@Override
	public void onQuoteBalanceUpdate(BigDecimal balance) {
		BigDecimal bidPriceProposal;
		synchronized (priceProposals) {
			bidPriceProposal = priceProposals.get(Side.BUY).proposedPrice;
		}
		
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
    
	String placeOrder(Side side, BigDecimal price, BigDecimal size) {
		try {
			final String clientOrderId = UUID.randomUUID().toString();
			Log.infof("Placing new limit order - clOrdId: %s, side: %s, price: %s, size: %s",
					clientOrderId, side, price, size);
			OrderCreateResponse response;
			response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
					.clientOid(clientOrderId)
					.side(side.name().toLowerCase())
					.symbol(symbol)
					.price(price)
					.size(size)
					.type(LIMIT)
					.build());
			orders.put(response.getOrderId(), new Order(response.getOrderId(), Side.SELL, size, price));
			return clientOrderId;
		} catch (IOException e) {
			throw new IllegalStateException("Could not place order", e);
		}
	}
	
	void cancelOrders(Side side, BigDecimal proposedPrice) {
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
}
