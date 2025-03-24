package cz.amuradon.tralon.cexliquidityminer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.OrderCreateResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlaceNewOrders {
	
	private static final String LIMIT = "limit";

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaceNewOrders.class);
	
	private final KucoinRestClient restClient;
    
	private final String symbol;
	
	private final BigDecimal maxBalanceToUse;
	
    private final Map<String, Order> orders;
    
    private final Map<Side, PriceProposal> priceProposals;
    
    @Inject
    public PlaceNewOrders(final KucoinRestClient restClient,
    		@ConfigProperty(name = "baseToken") final String baseToken,
    		@ConfigProperty(name = "quoteToken") final String quoteToken,
    		@ConfigProperty(name = "maxQuoteBalanceToUse") final int maxBalanceToUse,
    		final Map<String, Order> orders,
    		final Map<Side, PriceProposal> priceProposals) {
		this.restClient = restClient;
		symbol = baseToken + "-" + quoteToken;
		this.maxBalanceToUse = new BigDecimal(maxBalanceToUse);
		this.orders = orders;
		this.priceProposals = priceProposals;
    }

    public void processOrderChanges(BalanceHolder balanceHolder) {
      	BigDecimal baseBalanceSnapshot = balanceHolder.getBaseBalance();
       	BigDecimal quoteBalanceSnapshot = balanceHolder.getQuoteBalance();
       	
       	BigDecimal askPriceProposal;
       	BigDecimal bidPriceProposal;
       	synchronized (priceProposals) {
       		askPriceProposal = priceProposals.get(Side.SELL).proposedPrice;
       		bidPriceProposal = priceProposals.get(Side.BUY).proposedPrice;
		}
        
		try {
			if (baseBalanceSnapshot.compareTo(BigDecimal.ZERO) > 0) {
				final String clientOrderId = UUID.randomUUID().toString();
				LOGGER.info("Placing new limit order - clOrdId: {}, side: {}, price: {}, size: {}",
						clientOrderId, Side.SELL, askPriceProposal, baseBalanceSnapshot);
				OrderCreateResponse response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
						.clientOid(clientOrderId)
						.side("sell")
						.symbol(symbol)
						.price(askPriceProposal)
						.size(baseBalanceSnapshot)
						.type(LIMIT)
						.build());
				orders.put(response.getOrderId(), new Order(response.getOrderId(), Side.SELL, baseBalanceSnapshot, askPriceProposal));
			} else {
				LOGGER.info("No new sell orders placed");
			}
			
			BigDecimal balanceQuoteLeftForBids = maxBalanceToUse;
			for (Order order : orders.values()) {
				balanceQuoteLeftForBids = balanceQuoteLeftForBids.subtract(order.price().multiply(order.size()));
			}
			balanceQuoteLeftForBids = balanceQuoteLeftForBids.min(quoteBalanceSnapshot);
			
			BigDecimal size = balanceQuoteLeftForBids.divide(bidPriceProposal, 4, RoundingMode.FLOOR);
			if (size.compareTo(BigDecimal.ZERO) > 0) {
				final String clientOrderId = UUID.randomUUID().toString();
				LOGGER.info("Placing new limit order - clOrdId: {}, side: {}, price: {}, size: {}",
						clientOrderId, Side.BUY, bidPriceProposal, size);
				OrderCreateResponse response = restClient.orderAPI().createOrder(OrderCreateApiRequest.builder()
						.clientOid(clientOrderId)
						.side("buy")
						.symbol(symbol)
						.price(bidPriceProposal)
						.size(size)
						.type(LIMIT)
						.build());
				orders.put(response.getOrderId(), new Order(response.getOrderId(), Side.BUY, size, bidPriceProposal));
			} else {
				LOGGER.info("No new buy orders placed");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not create orders", e);
		}
    }
    
    
}
