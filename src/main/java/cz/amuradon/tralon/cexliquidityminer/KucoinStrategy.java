package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;
import com.kucoin.sdk.websocket.event.KucoinEvent;
import com.kucoin.sdk.websocket.event.Level2Event;
import com.kucoin.sdk.websocket.event.OrderChangeEvent;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
	
    private Map<String, Order> orders;
    
    private BigDecimal baseBalance = BigDecimal.ZERO;
    
    private BigDecimal quoteBalance = BigDecimal.ZERO;
    
    private long lastBalanceChange;
    
    private long lastOrderChange;
    
    public KucoinStrategy(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate, final String baseToken, final String quoteToken,
    		final int sideVolumeThreshold, final int maxBalanceToUse) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		symbol = baseToken + "-" + quoteToken;
		this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
		lastOrderChange = lastBalanceChange;
    }

    public void run() {
        try {
        	orders = restClient.orderAPI().listOrders(symbol, null, null, null, "active", null, null, 20, 1).getItems()
        		.stream().collect(Collectors.toMap(r -> r.getId(), r -> new Order(r.getId(), r.getSide(), r.getSize(), r.getPrice())));
        	LOGGER.info("Current orders {}", orders);
        	
        	lastBalanceChange = new Date().getTime();
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
        BigDecimal askPriceLevel = getTargetPriceLevel(data.getAsks());
        BigDecimal bidPriceLevel = getTargetPriceLevel(data.getBids());
        LOGGER.debug("Target ask price: {}", askPriceLevel);
        LOGGER.debug("Target bid price: {}", bidPriceLevel);
        
        // Jak zjistit, ze jsem posledni v rade na dane price level
        for (Iterator<Entry<String, Order>> it = orders.entrySet().iterator(); it.hasNext(); ) {
        	Entry<String, Order> entry = it.next();
        	Order order = entry.getValue();
        	if ((BUY.equalsIgnoreCase(order.side()) && order.price().compareTo(bidPriceLevel) != 0)
        			|| (SELL.equalsIgnoreCase(order.side()) && order.price().compareTo(askPriceLevel) != 0)) {
        		try {
        			// Wait for balance update
        			lastOrderChange = Long.MAX_VALUE;
        			lastBalanceChange = Long.MAX_VALUE;
					restClient.orderAPI().cancelOrder(order.orderId());
					it.remove();
					LOGGER.info("Cancelling order {}", order);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
        
        // Vyuzivam timestamp na zpravach k synchronizaci mezi async udalostmi
        long timestamp = data.getTimestamp();
		if (timestamp > lastBalanceChange && timestamp > lastOrderChange) {
			try {
				if (baseBalance.compareTo(BigDecimal.ZERO) > 0) {
					final String clientOrderId = UUID.randomUUID().toString();
					restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
							.clientOid(clientOrderId)
							.side(SELL)
							.symbol(symbol)
							.price(askPriceLevel)
							.size(baseBalance)
							.type(LIMIT)
							.build());
					LOGGER.info("Placing new limit order - clOrdId: {}, side: {}, price: {}, size: {}",
							clientOrderId, SELL, askPriceLevel, baseBalance);
				} else {
					LOGGER.info("No new sell orders placed");
				}
				
				BigDecimal balanceQuoteLeftForBids = maxBalanceToUse.min(quoteBalance)
						.subtract(askPriceLevel.multiply(baseBalance));
				for (Order order : orders.values()) {
					balanceQuoteLeftForBids = balanceQuoteLeftForBids.subtract(order.price().multiply(order.size()));
				}
				
				BigDecimal size = balanceQuoteLeftForBids.divide(bidPriceLevel, 6, RoundingMode.FLOOR);
				if (size.compareTo(BigDecimal.ZERO) > 0) {
					final String clientOrderId = UUID.randomUUID().toString();
					restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
							.clientOid(clientOrderId)
							.side(BUY)
							.symbol(symbol)
							.price(bidPriceLevel)
							.size(size)
							.type(LIMIT)
							.build());
					LOGGER.info("Placing new limit order - clOrdId: {}, side: {}, price: {}, size: {}",
							clientOrderId, BUY, bidPriceLevel, size);
				} else {
					LOGGER.info("No new buy orders placed");
				}
			} catch (IOException e) {
				throw new IllegalStateException("Could not create orders", e);
			}
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
    	lastOrderChange = data.getTs();
		String changeType = data.getType();
		if (symbol.equalsIgnoreCase(data.getSymbol())) {
    		if ("open".equalsIgnoreCase(changeType)) {
    			orders.put(data.getOrderId(), new Order(data.getOrderId(), data.getSide(), data.getSize(), data.getPrice()));
    		} else if ("filled".equalsIgnoreCase(changeType)) {
    			orders.remove(data.getOrderId());
    		} else if ("cancelled".equalsIgnoreCase(changeType)) {
    			orders.remove(data.getOrderId());
    		} else if ("match".equalsIgnoreCase(changeType)) {
    			orders.get(data.getOrderId()).size(data.getRemainSize());
    		}
    	}
		LOGGER.info("Order change: {}, ID: {}, type: {}", lastOrderChange, data.getOrderId(), changeType);
    }
    
    private void onAccountBalance(KucoinEvent<AccountChangeEvent> event) {
    	LOGGER.debug("{}", event);
    	AccountChangeEvent data = event.getData();
    	lastBalanceChange = Long.parseLong(data.getTime());
    	if (baseToken.equalsIgnoreCase(data.getCurrency())) {
			baseBalance = data.getAvailable();
		} else if (quoteToken.equalsIgnoreCase(data.getCurrency())) {
			quoteBalance = data.getAvailable();
		}
    	LOGGER.info("Balance change: {}, {}: {}, {}: {}", lastBalanceChange, baseToken, baseBalance,
    			quoteToken, quoteBalance);
    	
    	// TODO Calculate new order proposals based on balance and existing orders, respect amount of balance to use
    }
}
