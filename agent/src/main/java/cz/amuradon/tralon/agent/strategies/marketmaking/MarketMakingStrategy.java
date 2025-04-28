package cz.amuradon.tralon.agent.strategies.marketmaking;

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

import cz.amuradon.tralon.agent.OrderBook;
import cz.amuradon.tralon.agent.OrderBookManager;
import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.PriceProposal;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderBookUpdate;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.model.Order;
import cz.amuradon.tralon.agent.model.OrderImpl;
import cz.amuradon.tralon.agent.strategies.Strategy;
import io.quarkus.logging.Log;

public class MarketMakingStrategy implements Strategy {

	private final RestClient restClient;
    
	private final String symbol;
	
    private final Map<String, Order> orders;
    
	private final int priceChangeDelayMs;
	
    private final Map<Side, PriceProposal> priceProposals;
    
	private final BigDecimal maxQuoteBalanceToUse;
	
	private final ScheduledExecutorService scheduler;

	private final Map<Side, ScheduledFuture<?>> cancelOrdersTasks;
	
    private final WebsocketClient websocketClient;
    
	private final String baseAsset;
	
	private final String quoteAsset;
	
    private final OrderBookManager orderBookManager;
    
    private final SpreadStrategy spreadCalculator;

	private boolean localOrderBookCreated;
	
    public MarketMakingStrategy(
    		final RestClient restClient,
    		final WebsocketClient websocketClient,
    		final String baseAsset,
    		final String quoteAsset,
    		final String symbol,
    		final int priceChangeDelayMs,
    		final BigDecimal maxQuoteBalanceToUse,
    		final ScheduledExecutorService scheduler,
    		final SpreadStrategy spreadCalculator) {
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.baseAsset = baseAsset;
		this.quoteAsset = quoteAsset;
		this.symbol = symbol;
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.maxQuoteBalanceToUse = maxQuoteBalanceToUse;
		this.scheduler = scheduler;
		this.orderBookManager = new OrderBookManager(restClient);
		this.spreadCalculator = spreadCalculator;
		this.orders = new ConcurrentHashMap<>();
		cancelOrdersTasks = new HashMap<>();
		
		priceProposals = new ConcurrentHashMap<>();
		priceProposals.put(Side.BUY, new PriceProposal());
		priceProposals.put(Side.SELL, new PriceProposal());
	}
    
    @Override
    public void start() {
    	restClient.cacheSymbolDetails(symbol);
    	
    	// Start consuming data from websockets
    	websocketClient.onOrderChange(this::onOrderChange);
    	websocketClient.onOrderBookChange(this::onOrderBookChange, symbol);

    	// Create local order book
    	// XXX promyslet synchronizaci celkove
    	OrderBook orderBook = orderBookManager.createLocalOrderBook(symbol);
    	localOrderBookCreated = true;
    	computeInitialPrices(orderBook);
    	
    	// Get existing orders
    	orders.clear();
    	orders.putAll(restClient.listOrders(symbol));
    	Log.infof("Current orders %s", orders);
    	
    	// Get existing balances
    	for (AccountBalance balance : restClient.listBalances()) {
			onAccountBalance(balance);
    	}

    	// Start balance websocket after initial call
    	websocketClient.onAccountBalance(this::onAccountBalance);
    	
    }
    
    @Override
    public void stop() {
    	// TODO Auto-generated method stub
    }
    
    private void onOrderBookChange(OrderBookChange event) {
    	// FIXME async processing? No queue for unprocessed data 
    	event.getAsks().stream().forEach(u ->  {
    		OrderBook ob = orderBookManager.processUpdate(u);
    		onOrderBookUpdate(u, ob.getOrderBookSide(u.side()));
    	});
    	event.getBids().stream().forEach(u -> {
    		OrderBook ob = orderBookManager.processUpdate(u);
    		onOrderBookUpdate(u, ob.getOrderBookSide(u.side()));
    	});
    }
    
    private void onOrderChange(OrderChange data) {
		OrderStatus orderStatus = data.status();
		if (symbol.equalsIgnoreCase(data.symbol())) {
			// Open order are added and cancelled are removed immediately when request sent over REST API
			// but this is to sync server state as well as record any manual intervention
			
    		if (orderStatus == OrderStatus.NEW) {
    			orders.put(data.orderId(), 
    					new OrderImpl(data.orderId(), data.symbol(),
    							Side.getValue(data.side()), data.quantity(), data.price()));
    		} else if (orderStatus == OrderStatus.FILLED) {
    			orders.remove(data.orderId());
    		} else if (orderStatus == OrderStatus.CANCELED) {
    			// The orders are removed immediately once cancelled, this is to cover manual cancel
    			orders.remove(data.orderId());
    		} else if (orderStatus == OrderStatus.PARTIALLY_FILLED) {
    			orders.get(data.orderId()).size(data.remainingQuantity());
    		}
    	}
		Log.infof("Order change: %s, ID: %s", data.orderId(), orderStatus);
		Log.infof("Orders in memory %s", orders);
    }
    
    private void onAccountBalance(AccountBalance accountBalance) {
    	String token = accountBalance.asset();
    	BigDecimal available = accountBalance.available();
		if (baseAsset.equalsIgnoreCase(token)) {
    		Log.infof("Base balance changed %s: %s", baseAsset, available);
    		// XXX is the split needed since Side is not passed as arg anymore
    		onBaseBalanceUpdate(available);
    	} else if (quoteAsset.equalsIgnoreCase(accountBalance.asset())) {
    		Log.infof("Quote balance changed %s: %s", quoteAsset, available);
    		onQuoteBalanceUpdate(available);
    	}
    }
	
    private void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		Side side = update.side();
		PriceProposal proposal = priceProposals.get(side);
		// TODO timestamp z update -> nezpracovat starsi update?
		// Serves as monitor for all things on the same side
		synchronized (proposal) {
			if (!side.isPriceOutOfRange(update.price(), proposal.currentPrice)
					&& localOrderBookCreated) {
				
				BigDecimal targetPrice = spreadCalculator.calculate(side, orderBookSide);
				
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
	
	private void computeInitialPrices(OrderBook orderBook) {
		long timestamp = new Date().getTime();
    	BigDecimal askPrice = spreadCalculator.calculate(Side.SELL, orderBook.getAsks());
    	PriceProposal askProposal = priceProposals.get(Side.SELL);
    	askProposal.currentPrice = askPrice;
    	askProposal.proposedPrice = askPrice;
    	askProposal.timestamp = timestamp;
    			
    	BigDecimal bidPrice = spreadCalculator.calculate(Side.BUY, orderBook.getBids());
    	PriceProposal bidProposal = priceProposals.get(Side.BUY);
    	bidProposal.currentPrice = bidPrice;
    	bidProposal.proposedPrice = bidPrice;
    	bidProposal.timestamp = timestamp;
    	
    	Log.debugf("First price proposals calculated - ask: %s, bid: %s", askPrice, bidPrice);
		
	}

	// FIXME Na Kucoin nechodi balance updates spolehlive!
	private void onBaseBalanceUpdate(BigDecimal balance) {
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

	private void onQuoteBalanceUpdate(BigDecimal balance) {
		PriceProposal priceProposal = priceProposals.get(Side.BUY);
		
		synchronized (priceProposal) {
			BigDecimal bidPriceProposal = priceProposal.proposedPrice;
			priceProposal.currentPrice = bidPriceProposal;
			
			BigDecimal balanceQuoteLeftForBids = maxQuoteBalanceToUse;
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
				.send().orderId();
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
				restClient.cancelOrder(order.orderId(), order.symbol());
        	} else {
        		ordersBeKept.put(entry.getKey(), order);
        	}
        }
        
        synchronized (orders) {
        	orders.clear();
        	orders.putAll(ordersBeKept);
		}
    }

	@Override
	public String getDescription() {
		return String.format("%s - symbol: %s/%s, price change delay (ms): %d"
				+ " , max balance to use: %s, spread: %",
				getClass().getSimpleName(), baseAsset, quoteAsset, priceChangeDelayMs, maxQuoteBalanceToUse, spreadCalculator.describe());
	}
}
