package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.rest.response.OrderCreateResponse;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;
import com.kucoin.sdk.websocket.event.KucoinEvent;
import com.kucoin.sdk.websocket.event.Level2Event;
import com.kucoin.sdk.websocket.event.OrderChangeEvent;

public class KucoinStrategy {
	
	/* TODO
	 * - support multiple orders
	 * - pouzit volume ke kalkulaci volatility?
	 * - drzet se vzdy na konci rady na dane cenove urovni v order book
	 * - flexibilni spread - drzet se za zdi dane velikosti ?
	 *   - nepocitam ted spread, ale pouzivam order book - na 5. urovni v order book bez pocitani volume pred
	 *   - pocitat, kolik volume je pred v order book?
	 * - pokud existuje available balance, ale order uz existuje, vytvorit dalsi nebo reset?
	 * - delay pro reakci s ordery pro vice volatilni tokeny
	 * */

	private static final String LIMIT = "limit";

	private static final String SELL = "sell";

	private static final String BUY = "buy";

	private static final Logger LOGGER = LoggerFactory.getLogger(KucoinStrategy.class);
	
	private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final String symbol;
	
	private final BigDecimal sideVolumeThreshold;
	
	private final BigDecimal maxBalanceToUse;
	
	private final int priceChangeDelayMs;
	
	private final ExecutorService executorService;
	
	private final Object monitor;
	
    private Map<String, Order> orders;
    
    private BigDecimal baseBalance = BigDecimal.ZERO;
    
    private BigDecimal quoteBalance = BigDecimal.ZERO;
    
    private BigDecimal currentBidPriceLevel;
    
    private BigDecimal currentAskPriceLevel;
    
    private BigDecimal bidPriceLevelProposal;
    
    private BigDecimal askPriceLevelProposal;
    
    private long askPriceLevelProposalTimestamp;
   
    private long bidPriceLevelProposalTimestamp;
    
    private AtomicInteger waitForBalanceUpdate;
    
    public KucoinStrategy(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate, final String baseToken, final String quoteToken,
    		final int sideVolumeThreshold, final int maxBalanceToUse,
    		final int priceChangeDelayMs) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		symbol = baseToken + "-" + quoteToken;
		this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
		this.priceChangeDelayMs = priceChangeDelayMs;
		this.executorService = Executors.newSingleThreadExecutor();
		this.monitor = new Object();
		currentBidPriceLevel = BigDecimal.ZERO;
		currentAskPriceLevel = BigDecimal.ZERO;
		bidPriceLevelProposal = BigDecimal.ZERO;
		askPriceLevelProposal = BigDecimal.ZERO;
		waitForBalanceUpdate = new AtomicInteger(0);
    }

    public void run() {
        try {
        	orders = restClient.orderAPI().listOrders(symbol, null, null, null, "active", null, null, 20, 1).getItems()
        		.stream().collect(Collectors.toConcurrentMap(r -> r.getId(), r -> new Order(r.getId(), r.getSide(), r.getSize(), r.getPrice())));
        	LOGGER.info("Current orders {}", orders);
        	
        	for (AccountBalancesResponse balance : restClient.accountAPI().listAccounts(null, "trade")) {
        		if (baseToken.equalsIgnoreCase(balance.getCurrency())) {
        			baseBalance = balance.getAvailable();
        		} else if (quoteToken.equalsIgnoreCase(balance.getCurrency())) {
        			quoteBalance = balance.getAvailable();
        		}
        	}
        	LOGGER.info("Available base balance {}: {}", baseToken, baseBalance);
        	LOGGER.info("Available quote balance {}: {}", quoteToken, quoteBalance);
        	
            wsClientPublic.onLevel2Data(50, this::onLevel2Data, symbol);
            wsClientPrivate.onOrderChange(this::onOrderChange);
            wsClientPrivate.onAccountBalance(this::onAccountBalance);
        } catch (IOException e) {
            throw new IllegalStateException("Could not be initiated.", e);
        }
    }

    private void onLevel2Data(KucoinEvent<Level2Event> event) {
    	LOGGER.debug("{}", event);
        Level2Event data = event.getData();
        long timestamp = data.getTimestamp();

        BigDecimal askPriceLevel = getTargetPriceLevel(data.getAsks());
        BigDecimal bidPriceLevel = getTargetPriceLevel(data.getBids());
        
        LOGGER.debug("Target ask price: {}", askPriceLevel);
        LOGGER.debug("Target bid price: {}", bidPriceLevel);
        
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
        	
        	executorService.execute(this::processOrderChanges);
        }
    }
    
    private void processOrderChanges() {
    	// TODO Jak zjistit, ze jsem posledni v rade na dane price level
    	Map<String, Order> ordersBeKept = new ConcurrentHashMap<>();
        for (Entry<String, Order> entry: orders.entrySet()) {
        	Order order = entry.getValue();
        	if ((BUY.equalsIgnoreCase(order.side()) && order.price().compareTo(bidPriceLevelProposal) != 0)
        			|| (SELL.equalsIgnoreCase(order.side()) && order.price().compareTo(askPriceLevelProposal) != 0)) {
        		try {
        			// Wait for balance updates
        			waitForBalanceUpdate.incrementAndGet();
        			
        			LOGGER.info("Cancelling order {}", order);
					restClient.orderAPI().cancelOrder(order.orderId());
				} catch (IOException e) {
					throw new IllegalStateException("Could not cancel an order " + order.orderId(), e);
				}
        	} else {
        		ordersBeKept.put(entry.getKey(), order);
        	}
        }
        orders = ordersBeKept;
        
        long timeout = 10000;
        long endtime = new Date().getTime() + timeout;
       	while (waitForBalanceUpdate.get() > 0 && new Date().getTime() < endtime) {
        	try {
        		synchronized (monitor) {
        			monitor.wait(timeout);
        		}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Thread wait() went wrong", e);
			}
        }
       	
       	// It needs to use the current snapshot of balance as async events can change it intra-processing
       	final BigDecimal baseBalanceSnapshot = baseBalance;
       	final BigDecimal quoteBalanceSnapshot = quoteBalance;
        
		try {
			if (baseBalanceSnapshot.compareTo(BigDecimal.ZERO) > 0) {
				final String clientOrderId = UUID.randomUUID().toString();
				LOGGER.info("Placing new limit order - clOrdId: {}, side: {}, price: {}, size: {}",
						clientOrderId, SELL, askPriceLevelProposal, baseBalanceSnapshot);
				OrderCreateResponse response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
						.clientOid(clientOrderId)
						.side(SELL)
						.symbol(symbol)
						.price(askPriceLevelProposal)
						.size(baseBalanceSnapshot)
						.type(LIMIT)
						.build());
				orders.put(response.getOrderId(), new Order(response.getOrderId(), SELL, baseBalanceSnapshot, askPriceLevelProposal));
			} else {
				LOGGER.info("No new sell orders placed");
			}
			
			BigDecimal balanceQuoteLeftForBids = maxBalanceToUse;
			for (Order order : orders.values()) {
				balanceQuoteLeftForBids = balanceQuoteLeftForBids.subtract(order.price().multiply(order.size()));
			}
			balanceQuoteLeftForBids = balanceQuoteLeftForBids.min(quoteBalanceSnapshot);
			
			BigDecimal size = balanceQuoteLeftForBids.divide(bidPriceLevelProposal, 4, RoundingMode.FLOOR);
			if (size.compareTo(BigDecimal.ZERO) > 0) {
				final String clientOrderId = UUID.randomUUID().toString();
				LOGGER.info("Placing new limit order - clOrdId: {}, side: {}, price: {}, size: {}",
						clientOrderId, BUY, bidPriceLevelProposal, size);
				OrderCreateResponse response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
						.clientOid(clientOrderId)
						.side(BUY)
						.symbol(symbol)
						.price(bidPriceLevelProposal)
						.size(size)
						.type(LIMIT)
						.build());
				orders.put(response.getOrderId(), new Order(response.getOrderId(), BUY, size, bidPriceLevelProposal));
			} else {
				LOGGER.info("No new buy orders placed");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not create orders", e);
		}
    }
    
    private BigDecimal getTargetPriceLevel(Object[][] data) {
    	BigDecimal volume = BigDecimal.ZERO;
    	int i = 0;
    	BigDecimal price = BigDecimal.ZERO;
    	while (i < data.length && volume.compareTo(sideVolumeThreshold) < 0) {
    		price = new BigDecimal(data[i][0].toString());
			var quantity = new BigDecimal(data[i][1].toString());
			volume = volume.add(price.multiply(quantity));
			i++;
		}
    	return price;
    }
    
    private void onOrderChange(KucoinEvent<OrderChangeEvent> event) {
    	LOGGER.debug("{}", event);
    	OrderChangeEvent data = event.getData();
		String changeType = data.getType();
		if (symbol.equalsIgnoreCase(data.getSymbol())) {
			// Open order are added and cancelled are removed immediately when request sent over REST API
			// but this is to sync server state as well as record any manual intervention
			
    		if ("open".equalsIgnoreCase(changeType)) {
    			orders.put(data.getOrderId(), new Order(data.getOrderId(), data.getSide(), data.getSize(), data.getPrice()));
    		} else if ("filled".equalsIgnoreCase(changeType)) {
    			orders.remove(data.getOrderId());
    		} else if ("cancelled".equalsIgnoreCase(changeType)) {
    			// The orders are removed immediately once cancelled, this is to cover manual cancel
    			orders.remove(data.getOrderId());
    		} else if ("match".equalsIgnoreCase(changeType)) {
    			orders.get(data.getOrderId()).size(data.getRemainSize());
    		}
    	}
		LOGGER.info("Order change: {}, ID: {}, type: {}", data.getOrderId(), changeType);
		LOGGER.info("Orders in memory {}", orders);
    }
    
    private void onAccountBalance(KucoinEvent<AccountChangeEvent> event) {
    	LOGGER.debug("{}", event);
    	AccountChangeEvent data = event.getData();
    	waitForBalanceUpdate.updateAndGet(i -> Math.max(0, i - 1));
    	if (baseToken.equalsIgnoreCase(data.getCurrency())) {
			baseBalance = data.getAvailable();
		} else if (quoteToken.equalsIgnoreCase(data.getCurrency())) {
			quoteBalance = data.getAvailable();
		}
    	synchronized (monitor) {
    		monitor.notifyAll();
		}
    	LOGGER.info("Balance change: {}, {}: {}, {}: {}", baseToken, baseBalance,
    			quoteToken, quoteBalance);
    }
}
