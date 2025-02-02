package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
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
	 * */

	private static final Logger LOGGER = LoggerFactory.getLogger(KucoinStrategy.class);
	
    private Map<String, Order> orders;
    
    private BigDecimal baseBalance = BigDecimal.ZERO;
    
    private BigDecimal quoteBalance = BigDecimal.ZERO;
    
    private String baseToken;
    
    private String quoteToken;
    
    private String symbol;
    
    private long lastBalanceChange;
    
    private long lastOrderChange;
    
    private BigDecimal sideVolumeThreshold;
    
    private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final List<Order> orderProposals;
    
    public KucoinStrategy(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate, final String baseToken, final String quoteToken,
    		final int sideVolumeThreshold) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		symbol = baseToken + "-" + quoteToken;
		this.sideVolumeThreshold = new BigDecimal(sideVolumeThreshold);
		lastBalanceChange = new Date().getTime();
		lastOrderChange = lastBalanceChange;
		orderProposals = new ArrayList<>();
    }

    public void run() {
        try {
        	orders = restClient.orderAPI().listOrders(symbol, null, null, null, "active", null, null, 20, 1).getItems()
        		.stream().collect(Collectors.toMap(r -> r.getId(), r -> new Order(r.getId(), r.getSide(), r.getSize(), r.getPrice())));
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
        BigDecimal askPriceLevel = getTargetPriceLevel(data.getAsks());
        BigDecimal bidPriceLevel = getTargetPriceLevel(data.getBids());
        LOGGER.debug("Target ask price: {}", askPriceLevel);
        LOGGER.debug("Target bid price: {}", bidPriceLevel);
        
        // Existuji ordery? -> zaloz
        // Jsou order na 5. urovni? -ne-> predelej
        // Cancel order -> pockat na CO event a balance event
        // Jak zjistit, ze jsem posledni v rade na dane price level
        for (Order order: orders.values()) {
        	if (("buy".equalsIgnoreCase(order.side()) && order.price().compareTo(bidPriceLevel) != 0)
        			|| ("sell".equalsIgnoreCase(order.side()) && order.price().compareTo(askPriceLevel) != 0)) {
        		try {
        			lastOrderChange = Long.MAX_VALUE;
        			lastBalanceChange = Long.MAX_VALUE;
					restClient.orderAPI().cancelOrder(order.orderId());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
        
        // Vyuzivam timestamp na zpravach k synchronizaci mezi async udalostmi
        long timestamp = data.getTimestamp();
		if (timestamp > lastBalanceChange
				&& timestamp > lastOrderChange
				&& baseBalance.compareTo(BigDecimal.ZERO) > 0
				// && quoteBalance.
				) {
			// Vytvor nove ordery
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
