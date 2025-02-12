package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Body;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.OrderCreateResponse;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named(OrderChangesProcessor.BEAN_NAME)
@RegisterForReflection
public class OrderChangesProcessor {
	
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

	public static final String BEAN_NAME = "orderChangesProcessor";

	private static final String LIMIT = "limit";

	private static final String SELL = "sell";

	private static final String BUY = "buy";

	private static final Logger LOGGER = LoggerFactory.getLogger(OrderChangesProcessor.class);
	
	private final KucoinRestClient restClient;
    
	private final String symbol;
	
	private final BigDecimal maxBalanceToUse;
	
	private final BalanceMonitor balanceMonitor;
	
	private final BalanceHolder balanceHolder;
	
    private final Map<String, Order> orders;
    
    @Inject
    public OrderChangesProcessor(final KucoinRestClient restClient, final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate,
    		@ConfigProperty(name = "baseToken") final String baseToken,
    		@ConfigProperty(name = "quoteToken") final String quoteToken,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final BalanceMonitor balanceMonitor,
    		final BalanceHolder balanceHolder,
    		final Map<String, Order> orders) {
		this.restClient = restClient;
		symbol = baseToken + "-" + quoteToken;
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
		this.balanceMonitor = balanceMonitor;
		this.balanceHolder = balanceHolder;
		this.orders = orders;
    }

    public void processOrderChanges(@Body PriceLevelProposalHolder priceLevelProposalHolder) {
    	BigDecimal askPriceLevelProposal = priceLevelProposalHolder.askPriceLevelProposal();
    	BigDecimal bidPriceLevelProposal = priceLevelProposalHolder.bidPriceLevelProposal();
    	
    	// TODO Jak zjistit, ze jsem posledni v rade na dane price level
    	Map<String, Order> ordersBeKept = new ConcurrentHashMap<>();
        for (Entry<String, Order> entry: orders.entrySet()) {
        	Order order = entry.getValue();
        	if ((BUY.equalsIgnoreCase(order.side()) && order.price().compareTo(bidPriceLevelProposal) != 0)
        			|| (SELL.equalsIgnoreCase(order.side()) && order.price().compareTo(askPriceLevelProposal) != 0)) {
        		try {
        			// Wait for balance updates
        			balanceHolder.getWaitForBalanceUpdate().incrementAndGet();
        			
        			LOGGER.info("Cancelling order {}", order);
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
        
        long timeout = 10000;
        long endtime = new Date().getTime() + timeout;
       	while (balanceHolder.getWaitForBalanceUpdate().get() > 0 && new Date().getTime() < endtime) {
        	try {
        		synchronized (balanceMonitor) {
        			balanceMonitor.wait(timeout);
        		}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Thread wait() went wrong", e);
			}
        }
       	
       	// It needs to use the current snapshot of balance as async events can change it intra-processing
       	BigDecimal baseBalanceSnapshot;
       	BigDecimal quoteBalanceSnapshot;
       	synchronized (balanceHolder) {
       		baseBalanceSnapshot = balanceHolder.getBaseBalance();
       		quoteBalanceSnapshot = balanceHolder.getQuoteBalance();
		}
        
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
    
    
}
