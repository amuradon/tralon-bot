package cz.amuradon.tralon.cexliquiditymining.strategies;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.interfaces.OrderAPI;

import cz.amuradon.tralon.cexliquiditymining.Order;
import cz.amuradon.tralon.cexliquiditymining.OrderBookUpdate;
import cz.amuradon.tralon.cexliquiditymining.PriceProposal;
import cz.amuradon.tralon.cexliquiditymining.Side;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AbstractStrategyTest {

	@Mock
	private KucoinRestClient restClientMock;
	
	@Mock
	private OrderAPI orderApiMock;
	
	@Mock
	private ScheduledExecutorService schedulerMock;
	
	private AbstractStrategy strategy;
	
	private Map<BigDecimal, BigDecimal> bids;
	
	private Map<Side, PriceProposal> priceProposals;
	
	private HashMap<String, Order> orders;
	
	@BeforeEach
	public void prepare() {
		when(restClientMock.orderAPI()).thenReturn(orderApiMock);
		
		priceProposals = new HashMap<>();
		priceProposals.put(Side.BUY, new PriceProposal());
		priceProposals.put(Side.SELL, new PriceProposal());
		
		orders = new HashMap<>();
		
		// Using MM as the simplest implementation, might change in the future
		strategy = new MarketMakingStrategy(10, priceProposals, restClientMock, "TKN", "USDT", 100, orders,
				schedulerMock);
		
		bids = new TreeMap<>(Comparator.reverseOrder());
		bids.put(new BigDecimal("0.98"), new BigDecimal("20"));
		bids.put(new BigDecimal("0.97"), new BigDecimal("30"));
	}
	
	@Test
	public void newPriceAfterSamePrice() throws IOException {
		PriceProposal proposal = priceProposals.get(Side.BUY);
		proposal.currentPrice = new BigDecimal("0.98");
		proposal.proposedPrice = new BigDecimal("0.98");
		proposal.timestamp = 1000;
		
		final String orderId = "orderId";
		orders.put(orderId, new Order(orderId, Side.BUY, new BigDecimal(5), new BigDecimal("0.98")));
		
		strategy.onOrderBookUpdate(new OrderBookUpdate(1, new BigDecimal("0.98"), new BigDecimal("5"), Side.BUY, 1010), bids);
		verify(orderApiMock, never()).cancelOrder(orderId);
		
		bids.put(new BigDecimal("0.99"), new BigDecimal("10"));
		strategy.onOrderBookUpdate(new OrderBookUpdate(2, new BigDecimal("0.99"), new BigDecimal("10"), Side.BUY, 1020), bids);
		verify(orderApiMock).cancelOrder(orderId);
	}

	@Test
	public void samePrice() throws IOException {
		PriceProposal proposal = priceProposals.get(Side.BUY);
		proposal.currentPrice = new BigDecimal("0.98");
		proposal.proposedPrice = new BigDecimal("0.98");
		proposal.timestamp = 1000;
		
		final String orderId = "orderId";
		orders.put(orderId, new Order(orderId, Side.BUY, new BigDecimal(5), new BigDecimal("0.98")));
		
		strategy.onOrderBookUpdate(new OrderBookUpdate(1, new BigDecimal("0.98"), new BigDecimal("5"), Side.BUY, 1005), bids);
		verify(orderApiMock, never()).cancelOrder(orderId);
		
		strategy.onOrderBookUpdate(new OrderBookUpdate(2, new BigDecimal("0.98"), new BigDecimal("10"), Side.BUY, 1020), bids);
		verify(orderApiMock, never()).cancelOrder(orderId);
	}

	@Test
	public void newPrice() throws IOException {
		PriceProposal proposal = priceProposals.get(Side.BUY);
		proposal.currentPrice = new BigDecimal("0.98");
		proposal.proposedPrice = new BigDecimal("0.98");
		proposal.timestamp = 1000;
		
		final String orderId = "orderId";
		orders.put(orderId, new Order(orderId, Side.BUY, new BigDecimal(5), new BigDecimal("0.98")));
		
		bids.put(new BigDecimal("0.99"), new BigDecimal("10"));
		strategy.onOrderBookUpdate(new OrderBookUpdate(1, new BigDecimal("0.99"), new BigDecimal("10"), Side.BUY, 1005), bids);
		verify(orderApiMock, never()).cancelOrder(orderId);
		
		strategy.onOrderBookUpdate(new OrderBookUpdate(2, new BigDecimal("0.99"), new BigDecimal("11"), Side.BUY, 1020), bids);
		verify(orderApiMock).cancelOrder(orderId);
	}

	@Test
	public void newPriceGlitch() throws IOException {
		PriceProposal proposal = priceProposals.get(Side.BUY);
		proposal.currentPrice = new BigDecimal("0.98");
		proposal.proposedPrice = new BigDecimal("0.98");
		proposal.timestamp = 1000;
		
		final String orderId = "orderId";
		orders.put(orderId, new Order(orderId, Side.BUY, new BigDecimal(5), new BigDecimal("0.98")));
		
		BigDecimal newPrice = new BigDecimal("0.99");
		bids.put(newPrice, new BigDecimal("10"));
		strategy.onOrderBookUpdate(new OrderBookUpdate(1, newPrice, new BigDecimal("10"), Side.BUY, 1005), bids);
		verify(orderApiMock, never()).cancelOrder(orderId);
		
		bids.remove(newPrice);
		strategy.onOrderBookUpdate(new OrderBookUpdate(2, newPrice, new BigDecimal(0), Side.BUY, 1020), bids);
		verify(orderApiMock, never()).cancelOrder(orderId);
	}
}
