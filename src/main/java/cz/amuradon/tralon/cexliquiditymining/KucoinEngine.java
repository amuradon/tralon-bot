package cz.amuradon.tralon.cexliquiditymining;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;
import com.kucoin.sdk.websocket.event.KucoinEvent;
import com.kucoin.sdk.websocket.event.Level2ChangeEvent;
import com.kucoin.sdk.websocket.event.OrderChangeEvent;

import io.quarkus.logging.Log;

public class KucoinEngine implements Runnable {
	
	/* TODO
	 * - support multiple orders
	 * - pouzit volume ke kalkulaci volatility?
	 * - drzet se vzdy na konci rady na dane cenove urovni v order book
	 * - flexibilni spread - drzet se za zdi dane velikosti ?
	 *   - nepocitam ted spread, ale pouzivam order book - na 5. urovni v order book bez pocitani volume pred
	 *   - pocitat, kolik volume je pred v order book?
	 * */

	private static final Logger LOGGER = LoggerFactory.getLogger(KucoinEngine.class);
	
	private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final String symbol;
	
    private final Map<String, Order> orders;
    
    private final OrderBookManager orderBookManager;
    
    private final BalanceHolder balanceHolder;
    
    private final PlaceNewOrders placeNewOrders;
    
    public KucoinEngine(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate, final String baseToken, final String quoteToken,
    		final Map<String, Order> orders,
    		final OrderBookManager orderBookManager,
    		final PlaceNewOrders placeNewOrders) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		symbol = baseToken + "-" + quoteToken;
		this.orders = orders;
		this.orderBookManager = orderBookManager;
		this.balanceHolder = new BalanceHolder();
		this.placeNewOrders = placeNewOrders;
    }

    public void run() {
        try {
        	// Start consuming data from websockets
            wsClientPrivate.onOrderChange(this::onOrderChange);
        	wsClientPublic.onLevel2Data(this::onL2RT, symbol);

        	// Create local order book
        	orderBookManager.createLocalOrderBook();
        	
        	// Get existing orders
			restClient.orderAPI().listOrders(symbol, null, null, null, "active", null, null, 20, 1).getItems().stream()
					.forEach(r -> orders.put(r.getId(),
							new Order(r.getId(), Side.getValue(r.getSide()), r.getSize(), r.getPrice())));
	    	LOGGER.info("Current orders {}", orders);
	    	
	    	// Get existing balances
	    	for (AccountBalancesResponse balance : restClient.accountAPI().listAccounts(null, "trade")) {
				onAccountBalance(new AccountBalance(balance.getCurrency(), balance.getAvailable()));
	    	}

	    	// Start balance websocket after initial call
	    	wsClientPrivate.onAccountBalance(this::onAccountBalance);
        } catch (IOException e) {
            throw new IllegalStateException("Could not be initiated.", e);
        }
    }
    
    private void onL2RT(KucoinEvent<Level2ChangeEvent> event) {
    	Log.trace(event);
    	
    	// XXX KuCoin imbecils do not parse timestamp from underlying message
    	long timestamp = new Date().getTime();

    	// FIXME async processing? No queue for unprocessed data 
    	event.getData().getChanges().getAsks().stream().forEach(l ->
    		orderBookManager.processUpdate(new OrderBookUpdate(Long.parseLong(l.get(2)), new BigDecimal(l.get(0)),
				new BigDecimal(l.get(1)), Side.SELL, timestamp)));
    	event.getData().getChanges().getBids().stream().forEach(l -> orderBookManager.processUpdate(
    			new OrderBookUpdate(Long.parseLong(l.get(2)), new BigDecimal(l.get(0)),
    					new BigDecimal(l.get(1)), Side.BUY, timestamp)));
    }
    
    private void onOrderChange(KucoinEvent<OrderChangeEvent> event) {
    	LOGGER.debug("{}", event);
    	OrderChangeEvent data = event.getData();
		String changeType = data.getType();
		if (symbol.equalsIgnoreCase(data.getSymbol())) {
			// Open order are added and cancelled are removed immediately when request sent over REST API
			// but this is to sync server state as well as record any manual intervention
			
    		if ("open".equalsIgnoreCase(changeType)) {
    			orders.put(data.getOrderId(), 
    					new Order(data.getOrderId(), Side.getValue(data.getSide()), data.getSize(), data.getPrice()));
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
    	onAccountBalance(new AccountBalance(data.getCurrency(), data.getAvailable()));
    }

    private void onAccountBalance(AccountBalance accountBalance) {
    	String token = accountBalance.token();
    	BigDecimal available = accountBalance.available();
		if (baseToken.equalsIgnoreCase(token)) {
    		balanceHolder.setBaseBalance(available);
    		LOGGER.info("Base balance changed {}: {}", baseToken, available);
    		// XXX is the split needed since Side is not passed as arg anymore
    		placeNewOrders.processOrderChanges(balanceHolder);
    	} else if (quoteToken.equalsIgnoreCase(accountBalance.token())) {
    		balanceHolder.setQuoteBalance(available);
    		LOGGER.info("Quote balance changed {}: {}", quoteToken, available);
    		placeNewOrders.processOrderChanges(balanceHolder);
    	}
    }
}
