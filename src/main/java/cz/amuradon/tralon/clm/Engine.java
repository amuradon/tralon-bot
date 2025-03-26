package cz.amuradon.tralon.clm;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.amuradon.tralon.clm.connector.AccountBalance;
import cz.amuradon.tralon.clm.connector.OrderBookChange;
import cz.amuradon.tralon.clm.connector.OrderChange;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.WebsocketClient;
import cz.amuradon.tralon.clm.strategies.Strategy;

public class Engine implements Runnable {
	
	/* TODO
	 * - support multiple orders
	 * - pouzit volume ke kalkulaci volatility?
	 * - drzet se vzdy na konci rady na dane cenove urovni v order book
	 * - flexibilni spread - drzet se za zdi dane velikosti ?
	 *   - nepocitam ted spread, ale pouzivam order book - na 5. urovni v order book bez pocitani volume pred
	 *   - pocitat, kolik volume je pred v order book?
	 * */

	private static final Logger LOGGER = LoggerFactory.getLogger(Engine.class);
	
	private final RestClient restClient;
    
    private final WebsocketClient websocketClient;
    
	private final String baseToken;
	
	private final String quoteToken;
	
	private final String symbol;
	
    private final Map<String, Order> orders;
    
    private final OrderBookManager orderBookManager;
    
    private final Strategy strategy;
    
    public Engine(final RestClient restClient,
    		final WebsocketClient websocketClient,
    		final String baseToken,
    		final String quoteToken,
    		final Map<String, Order> orders,
    		final OrderBookManager orderBookManager,
    		final Strategy strategy) {
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		symbol = baseToken + "-" + quoteToken;
		this.orders = orders;
		this.orderBookManager = orderBookManager;
		this.strategy = strategy;
    }

    public void run() {
    	// Start consuming data from websockets
    	websocketClient.onOrderChange(this::onOrderChange);
    	websocketClient.onLevel2Data(this::onL2RT, symbol);

    	// Create local order book
    	orderBookManager.createLocalOrderBook();
    	
    	// Get existing orders
    	orders.clear();
    	orders.putAll(restClient.listOrders(symbol));
    	LOGGER.info("Current orders {}", orders);
    	
    	// Get existing balances
    	for (AccountBalance balance : restClient.listBalances()) {
			onAccountBalance(balance);
    	}

    	// Start balance websocket after initial call
    	websocketClient.onAccountBalance(this::onAccountBalance);
    }
    
    private void onL2RT(OrderBookChange event) {
    	// FIXME async processing? No queue for unprocessed data 
    	event.getAsks().stream().forEach(u -> orderBookManager.processUpdate(u));
    	event.getBids().stream().forEach(u -> orderBookManager.processUpdate(u));
    }
    
    private void onOrderChange(OrderChange data) {
		String changeType = data.getType();
		if (symbol.equalsIgnoreCase(data.getSymbol())) {
			// Open order are added and cancelled are removed immediately when request sent over REST API
			// but this is to sync server state as well as record any manual intervention
			
    		if ("open".equalsIgnoreCase(changeType)) {
    			orders.put(data.getOrderId(), 
    					new Order(data.getOrderId(), Side.getValue(data.getSide()), data.getSize(), data.getPrice()));
    		} else if ("filled".equalsIgnoreCase(changeType)) {
    			orders.remove(data.getOrderId());
    		} else if ("canceled".equalsIgnoreCase(changeType)) {
    			// The orders are removed immediately once cancelled, this is to cover manual cancel
    			orders.remove(data.getOrderId());
    		} else if ("match".equalsIgnoreCase(changeType)) {
    			orders.get(data.getOrderId()).size(data.getRemainSize());
    		}
    	}
		LOGGER.info("Order change: {}, ID: {}, type: {}", data.getOrderId(), changeType);
		LOGGER.info("Orders in memory {}", orders);
    }
    
    private void onAccountBalance(AccountBalance accountBalance) {
    	String token = accountBalance.token();
    	BigDecimal available = accountBalance.available();
		if (baseToken.equalsIgnoreCase(token)) {
    		LOGGER.info("Base balance changed {}: {}", baseToken, available);
    		// XXX is the split needed since Side is not passed as arg anymore
    		strategy.onBaseBalanceUpdate(available);
    	} else if (quoteToken.equalsIgnoreCase(accountBalance.token())) {
    		LOGGER.info("Quote balance changed {}: {}", quoteToken, available);
    		strategy.onQuoteBalanceUpdate(available);
    	}
    }
}
