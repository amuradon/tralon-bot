package cz.amuradon.tralon.cexliquidityminer;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OrderBookManagerTest {

//	private static final BigDecimal BUY_PRICE_LEVEL = new BigDecimal("0.998");
//
//	private static final BigDecimal SELL_PRICE_LEVEL = new BigDecimal("1.003");
//
//	private static final BigDecimal DEFAULT_SIZE = new BigDecimal(100);
//
//	private static final int SEQUENCE = 20;
//	
//	private static final long DEFAULT_TIMESTAMP = new Date().getTime();
//
//	@Mock
//	private ProducerTemplate producerTemplateMock;
//	
//	private OrderBookManager manager;
//	
//	private OrderBook orderBook;
//	
//	@BeforeEach
//	public void prepare() {
//		Map<BigDecimal, BigDecimal> asks = new LinkedHashMap<>();
//		asks.put(new BigDecimal("1.002"), DEFAULT_SIZE);
//		asks.put(SELL_PRICE_LEVEL, DEFAULT_SIZE);
//		asks.put(new BigDecimal("1.004"), DEFAULT_SIZE);
//		
//		Map<BigDecimal, BigDecimal> bids = new LinkedHashMap<>();
//		bids.put(new BigDecimal("0.999"), DEFAULT_SIZE);
//		bids.put(BUY_PRICE_LEVEL, DEFAULT_SIZE);
//		bids.put(new BigDecimal("0.997"), DEFAULT_SIZE);
//
//		orderBook = new OrderBook();
//		orderBook.setSequence(SEQUENCE);
//		orderBook.getAsks().putAll(asks);
//		orderBook.getBids().putAll(bids);
//		
//		manager = new OrderBookManager(100, 1000, producerTemplateMock, orderBook);
//	}
//	
//	@Test
//	public void smallerUpdateSequenceShouldDoNothing() {
//		manager.processUpdate(
//				new OrderBookUpdate(SEQUENCE - 1, SELL_PRICE_LEVEL, new BigDecimal(200), Side.SELL, DEFAULT_TIMESTAMP));
//		assertEquals(DEFAULT_SIZE, orderBook.getAsks().get(SELL_PRICE_LEVEL));
//	}
//
//	@Test
//	public void updateSellPriceLevelSize() {
//		BigDecimal newSize = new BigDecimal(200);
//		manager.processUpdate(
//				new OrderBookUpdate(SEQUENCE + 1, SELL_PRICE_LEVEL, newSize, Side.SELL, DEFAULT_TIMESTAMP));
//		assertEquals(newSize, orderBook.getAsks().get(SELL_PRICE_LEVEL));
//	}
//
//	@Test
//	public void removeSellPriceLevel() {
//		BigDecimal newSize = new BigDecimal(0);
//		manager.processUpdate(
//				new OrderBookUpdate(SEQUENCE + 1, SELL_PRICE_LEVEL, newSize, Side.SELL, DEFAULT_TIMESTAMP));
//		assertNull(orderBook.getAsks().get(SELL_PRICE_LEVEL));
//	}
//
//	@Test
//	public void updateBuyPriceLevelSize() {
//		BigDecimal newSize = new BigDecimal(200);
//		manager.processUpdate(
//				new OrderBookUpdate(SEQUENCE + 1, BUY_PRICE_LEVEL, newSize, Side.BUY, DEFAULT_TIMESTAMP));
//		assertEquals(newSize, orderBook.getBids().get(BUY_PRICE_LEVEL));
//	}
//	
//	@Test
//	public void removeBuyPriceLevel() {
//		BigDecimal newSize = new BigDecimal(0);
//		manager.processUpdate(
//				new OrderBookUpdate(SEQUENCE + 1, BUY_PRICE_LEVEL, newSize, Side.BUY, DEFAULT_TIMESTAMP));
//		assertNull(orderBook.getBids().get(BUY_PRICE_LEVEL));
//	}
}
