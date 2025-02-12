package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.rest.response.OrderBookResponse;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;
import com.kucoin.sdk.websocket.event.KucoinEvent;
import com.kucoin.sdk.websocket.event.Level2ChangeEvent;
import com.kucoin.sdk.websocket.event.OrderChangeEvent;

import io.quarkus.logging.Log;

public class KucoinStrategy {
	
	/* TODO
	 * - support multiple orders
	 * - pouzit volume ke kalkulaci volatility?
	 * - drzet se vzdy na konci rady na dane cenove urovni v order book
	 * - flexibilni spread - drzet se za zdi dane velikosti ?
	 *   - nepocitam ted spread, ale pouzivam order book - na 5. urovni v order book bez pocitani volume pred
	 *   - pocitat, kolik volume je pred v order book?
	 * */

	private static final Logger LOGGER = LoggerFactory.getLogger(KucoinStrategy.class);
	
	private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final String symbol;
	
	private final BalanceMonitor balanceMonitor;
	
	private final BalanceHolder balanceHolder;
	
	private final ProducerTemplate producer;
	
    private final Map<String, Order> orders;
    
    private final OrderBook orderBook;
    
    public KucoinStrategy(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate, final String baseToken, final String quoteToken,
    		final BalanceMonitor balanceMonitor, final BalanceHolder balanceHolder,
    		final ProducerTemplate producer, final Map<String, Order> orders,
    		final OrderBook orderBook) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		symbol = baseToken + "-" + quoteToken;
		this.balanceMonitor = balanceMonitor;
		this.balanceHolder = balanceHolder;
		this.producer = producer;
		this.orders = orders;
		this.orderBook = orderBook;
    }

    public void run() {
        try {
        	restClient.orderAPI().listOrders(symbol, null, null, null, "active", null, null, 20, 1).getItems()
        		.stream().forEach(r -> orders.put(r.getId(), new Order(r.getId(), r.getSide(), r.getSize(), r.getPrice())));
        	LOGGER.info("Current orders {}", orders);
        	
        	for (AccountBalancesResponse balance : restClient.accountAPI().listAccounts(null, "trade")) {
				if (baseToken.equalsIgnoreCase(balance.getCurrency())) {
        			synchronized (balanceHolder) {
        				BigDecimal baseBalance = balance.getAvailable();
        				balanceHolder.setBaseBalance(baseBalance);
        				LOGGER.info("Available base balance {}: {}", baseToken, baseBalance);
					}
        		} else if (quoteToken.equalsIgnoreCase(balance.getCurrency())) {
        			synchronized (balanceHolder) {
        				BigDecimal quoteBalance = balance.getAvailable();
        				balanceHolder.setQuoteBalance(quoteBalance);
        				LOGGER.info("Available quote balance {}: {}", quoteToken, quoteBalance);
					}
        		}
        	}
        	
            wsClientPrivate.onOrderChange(this::onOrderChange);
            wsClientPrivate.onAccountBalance(this::onAccountBalance);
        	wsClientPublic.onLevel2Data(this::onL2RT, symbol);

        	OrderBookResponse orderBookResponse = restClient.orderBookAPI().getAllLevel2OrderBook(symbol);
        	LOGGER.info("Order Book response: seq {}\nAsks:\n{}\nBids:\n{}", orderBookResponse.getSequence(),
        			orderBookResponse.getAsks(), orderBookResponse.getBids());
        	orderBook.setSequence(Long.parseLong(orderBookResponse.getSequence()));
   			setOrderBookSide(orderBookResponse.getAsks(), orderBook.getAsks());
        	setOrderBookSide(orderBookResponse.getBids(), orderBook.getBids());
        	producer.sendBody(MyRouteBuilder.DIRECT_START_L2_MARKET_UPDATE_ROUTE, orderBook);
        } catch (IOException e) {
            throw new IllegalStateException("Could not be initiated.", e);
        }
    }
    
    private void setOrderBookSide(List<List<String>> list, final Map<BigDecimal, BigDecimal> map) {
    	for (List<String> element : list) {
			map.put(new BigDecimal(element.get(0)), new BigDecimal(element.get(1)));
		}
    }

    private void onL2RT(KucoinEvent<Level2ChangeEvent> event) {
    	Log.trace(event);
    	
    	// XXX KuCoin imbecils do not parse timestamp from underlying message
    	long timestamp = new Date().getTime();
    	
    	event.getData().getChanges().getAsks().stream().forEach(l -> producer.sendBody(
    			MyRouteBuilder.SEDA_LEVEL2_MARKET_UPDATE,
    			new OrderBookUpdate(Long.parseLong(l.get(2)), new BigDecimal(l.get(0)),
    					new BigDecimal(l.get(1)), Side.SELL, timestamp)));
    	event.getData().getChanges().getBids().stream().forEach(l -> producer.sendBody(
    			MyRouteBuilder.SEDA_LEVEL2_MARKET_UPDATE,
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
    	balanceHolder.getWaitForBalanceUpdate().updateAndGet(i -> Math.max(0, i - 1));
    	if (baseToken.equalsIgnoreCase(data.getCurrency())) {
    		synchronized (balanceHolder) {
				BigDecimal baseBalance = data.getAvailable();
				balanceHolder.setBaseBalance(baseBalance);
				LOGGER.info("Base balance changed {}: {}", baseToken, baseBalance);
			}
		} else if (quoteToken.equalsIgnoreCase(data.getCurrency())) {
			synchronized (balanceHolder) {
				BigDecimal quoteBalance = data.getAvailable();
				balanceHolder.setQuoteBalance(quoteBalance);
				LOGGER.info("Quote balance changed {}: {}", quoteToken, quoteBalance);
			}
		}
    	synchronized (balanceMonitor) {
    		balanceMonitor.notifyAll();
		}
    }
}
