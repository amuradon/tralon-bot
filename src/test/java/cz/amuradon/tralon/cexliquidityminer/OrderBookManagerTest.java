package cz.amuradon.tralon.cexliquidityminer;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OrderBookManagerTest {

	private static final BigDecimal BUY_PRICE_LEVEL = new BigDecimal("0.998");

	private static final BigDecimal SELL_PRICE_LEVEL = new BigDecimal("1.003");

	private static final BigDecimal DEFAULT_SIZE = new BigDecimal(100);

	private static final int SEQUENCE = 20;

	private OrderBookManager manager;
	
	private OrderBook orderBook;
	
	@BeforeEach
	public void prepare() {
		Map<BigDecimal, BigDecimal> asks = new LinkedHashMap<>();
		asks.put(new BigDecimal("1.002"), DEFAULT_SIZE);
		asks.put(SELL_PRICE_LEVEL, DEFAULT_SIZE);
		asks.put(new BigDecimal("1.004"), DEFAULT_SIZE);
		
		Map<BigDecimal, BigDecimal> bids = new LinkedHashMap<>();
		bids.put(new BigDecimal("0.999"), DEFAULT_SIZE);
		bids.put(BUY_PRICE_LEVEL, DEFAULT_SIZE);
		bids.put(new BigDecimal("0.997"), DEFAULT_SIZE);

		orderBook = new OrderBook(SEQUENCE, asks, bids);
		
		manager = new OrderBookManager();
		manager.processOrderBook(orderBook);
	}
	
	@Test
	public void smallerUpdateSequenceShouldDoNothing() {
		manager.processUpdate(
				new OrderBookUpdate(SEQUENCE - 1, SELL_PRICE_LEVEL, new BigDecimal(200), Side.SELL));
		assertEquals(DEFAULT_SIZE, orderBook.getAsks().get(SELL_PRICE_LEVEL));
	}

	@Test
	public void updateSellPriceLevelSize() {
		BigDecimal newSize = new BigDecimal(200);
		manager.processUpdate(
				new OrderBookUpdate(SEQUENCE + 1, SELL_PRICE_LEVEL, newSize, Side.SELL));
		assertEquals(newSize, orderBook.getAsks().get(SELL_PRICE_LEVEL));
	}

	@Test
	public void removeSellPriceLevel() {
		BigDecimal newSize = new BigDecimal(0);
		manager.processUpdate(
				new OrderBookUpdate(SEQUENCE + 1, SELL_PRICE_LEVEL, newSize, Side.SELL));
		assertNull(orderBook.getAsks().get(SELL_PRICE_LEVEL));
	}

	@Test
	public void updateBuyPriceLevelSize() {
		BigDecimal newSize = new BigDecimal(200);
		manager.processUpdate(
				new OrderBookUpdate(SEQUENCE + 1, BUY_PRICE_LEVEL, newSize, Side.BUY));
		assertEquals(newSize, orderBook.getBids().get(BUY_PRICE_LEVEL));
	}
	
	@Test
	public void removeBuyPriceLevel() {
		BigDecimal newSize = new BigDecimal(0);
		manager.processUpdate(
				new OrderBookUpdate(SEQUENCE + 1, BUY_PRICE_LEVEL, newSize, Side.BUY));
		assertNull(orderBook.getBids().get(BUY_PRICE_LEVEL));
	}
}
