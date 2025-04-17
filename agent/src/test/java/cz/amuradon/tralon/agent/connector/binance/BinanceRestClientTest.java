package cz.amuradon.tralon.agent.connector.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.spot.Market;
import com.binance.connector.client.impl.spot.Trade;

import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.binance.BinanceRestClient;
import cz.amuradon.tralon.agent.model.Order;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BinanceRestClientTest {

	private BinanceRestClient client;

	@Mock
	private SpotClient spotClientMock;
	
	@Mock
	private Trade tradeMock;
	
	@Mock
	private Market marketMock;
	
	@BeforeEach
	public void prepare() {
		when(spotClientMock.createTrade()).thenReturn(tradeMock);
		when(spotClientMock.createMarket()).thenReturn(marketMock);
		client = new BinanceRestClient(spotClientMock);
	}
	
	@Test
	public void testListOrders() {
		when(tradeMock.getOpenOrders(anyMap())).thenReturn(
				"""
				[
				  {
				    "symbol": "LTCBTC",
				    "orderId": 1,
				    "orderListId": -1,
				    "clientOrderId": "myOrder1",
				    "price": "0.1",
				    "origQty": "1.0",
				    "executedQty": "0.0",
				    "cummulativeQuoteQty": "0.0",
				    "status": "NEW",
				    "timeInForce": "GTC",
				    "type": "LIMIT",
				    "side": "BUY",
				    "stopPrice": "0.0",
				    "icebergQty": "0.0",
				    "time": 1499827319559,
				    "updateTime": 1499827319559,
				    "isWorking": true,
				    "origQuoteOrderQty": "0.000000",
				    "workingTime": 1499827319559,
				    "selfTradePreventionMode": "NONE"
				  }
				]
				""");
		
		Map<String, Order> orders = client.listOrders("LTCBTC");
		
		assertEquals(1, orders.size());
		Order order = orders.get("1");
		assertEquals("1", order.orderId());
		assertEquals("LTCBTC", order.symbol());
		assertEquals(Side.BUY, order.side());
		assertEquals(new BigDecimal("1.0"), order.size());
		assertEquals(new BigDecimal("0.1"), order.price());
	}

	@Test
	public void testListBalance() {
		when(tradeMock.account(anyMap())).thenReturn(
				"""
				{
				  "makerCommission": 15,
				  "takerCommission": 15,
				  "buyerCommission": 0,
				  "sellerCommission": 0,
				  "commissionRates": {
				    "maker": "0.00150000",
				    "taker": "0.00150000",
				    "buyer": "0.00000000",
				    "seller": "0.00000000"
				  },
				  "canTrade": true,
				  "canWithdraw": true,
				  "canDeposit": true,
				  "brokered": false,
				  "requireSelfTradePrevention": false,
				  "preventSor": false,
				  "updateTime": 123456789,
				  "accountType": "SPOT",
				  "balances": [
				    {
				      "asset": "BTC",
				      "free": "4723846.89208129",
				      "locked": "0.00000000"
				    },
				    {
				      "asset": "LTC",
				      "free": "4763368.68006011",
				      "locked": "0.00000000"
				    }
				  ],
				  "permissions": [
				    "SPOT"
				  ],
				  "uid": 354937868
				}
				""");
		
		List<? extends AccountBalance> balances = client.listBalances();
		
		assertEquals(2, balances.size());
		
		AccountBalance balance = balances.get(0);
		assertEquals("BTC", balance.asset());
		assertEquals(new BigDecimal("4723846.89208129"), balance.available());
		
	}

	@Test
	public void testCacheSymbolDetails() {
		// JSON simplified
		when(marketMock.exchangeInfo(anyMap())).thenReturn(
				"""
				{
				 "timezone":"UTC",
				 "serverTime":1743167127054,
				 "symbols":[
				 {
				  "symbol":"DCRUSDT",
				  "filters":[
				   {"filterType":"PRICE_FILTER","minPrice":"0.01000000","maxPrice":"100000.00000000","tickSize":"0.01000000"},
				   {"filterType":"LOT_SIZE","minQty":"0.00100000","maxQty":"900000.00000000","stepSize":"0.00100000"}
				  ]
				 }
				 ]
				}
				""");
		
		client.cacheSymbolDetails("DCRUSDT");
	}
}
