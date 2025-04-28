package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import cz.amuradon.tralon.agent.connector.OrderBookResponseImpl;

public class ComputeInitialPriceTest {

	@Test
	public void testSlippage() {
		ComputeInitialPrice compute = new ComputeInitialPrice("slippage:20.0");
		List<List<String>> asks = new ArrayList<>();
		asks.add(Lists.newArrayList("0.3", "5"));
		asks.add(Lists.newArrayList("0.35", "20"));
		asks.add(Lists.newArrayList("0.36", "30"));
		
		OrderBookResponseImpl orderBook = new OrderBookResponseImpl(1L, asks, new ArrayList<>()); 
		BigDecimal result = compute.execute("VPTUSDT",
				orderBook);
		Assertions.assertEquals(new BigDecimal("0.408000"), result);
	}
	
	@Test
	public void testFixed() {
		ComputeInitialPrice compute = new ComputeInitialPrice("manual:0.00125");
		BigDecimal result = compute.execute("VPTUSDT", null);
		Assertions.assertEquals(new BigDecimal("0.00125"), result);
	}
	
}
