package cz.amuradon.tralon.cexliquidityminer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.camel.ProducerTemplate;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.rest.response.OrderCreateResponse;
import com.kucoin.sdk.rest.response.OrderResponse;
import com.kucoin.sdk.websocket.KucoinAPICallback;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;
import com.kucoin.sdk.websocket.event.KucoinEvent;
import com.kucoin.sdk.websocket.event.Level2Event;
import com.kucoin.sdk.websocket.event.OrderChangeEvent;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KucoinStrategyTest {

	private static final String NEW_ORDER_ID2 = "newOrder2";

	private static final String NEW_ORDER_ID1 = "newOrder1";

	private static final int PRICE_CHANGE_DELAY = 1000;

	private static final String QUOTE_TOKEN = "USDT";

	private static final String BASE_TOKEN = "TKN";
	
	private static final String SYMBOL = BASE_TOKEN + "-" + QUOTE_TOKEN;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private KucoinRestClient restClientMock;
	
	@Mock
	private KucoinPrivateWSClient privateWsClientMock;
	
	@Mock
	private KucoinPublicWSClient publicWsClientMock;
	
	@Mock
	private OrderCreateResponse orderCreateResponseMock1;
	
	@Mock
	private OrderCreateResponse orderCreateResponseMock2;
	
	@Mock
	private ProducerTemplate producerTemplateMock;

	@Captor
	private ArgumentCaptor<KucoinAPICallback<KucoinEvent<Level2Event>>> l2EventCallbackCaptor;

	@Captor
	private ArgumentCaptor<KucoinAPICallback<KucoinEvent<OrderChangeEvent>>> orderChangeEventCallbackCaptor;

	@Captor
	private ArgumentCaptor<KucoinAPICallback<KucoinEvent<AccountChangeEvent>>> accountChangeEventCallbackCaptor;
	
	@Captor
	private ArgumentCaptor<OrderCreateApiRequest> orderCreateRequestCaptor;
	
	private KucoinAPICallback<KucoinEvent<Level2Event>> l2EventCallback;
	
	private KucoinAPICallback<KucoinEvent<OrderChangeEvent>> orderChangeEventCallback;
	
	private KucoinAPICallback<KucoinEvent<AccountChangeEvent>> accountChangeEventCallback;
	
	private List<OrderResponse> orderResponses;
	
	private List<AccountBalancesResponse> accountBalanceResponses;
	
	private KucoinStrategy strategy;
	
	private long timestamp;
	
	
	@BeforeEach
	public void prepare() throws IOException {
		orderResponses = new ArrayList<>();
		when(restClientMock.orderAPI().listOrders(anyString(), any(), any(), any(),
				anyString(), any(), any(), anyInt(), anyInt()).getItems()).thenReturn(orderResponses);
		
		accountBalanceResponses = new ArrayList<>();
		when(restClientMock.accountAPI().listAccounts(null, "trade")).thenReturn(accountBalanceResponses);
		
		when(orderCreateResponseMock1.getOrderId()).thenReturn(NEW_ORDER_ID1);
		when(orderCreateResponseMock2.getOrderId()).thenReturn(NEW_ORDER_ID2);
		when(restClientMock.orderAPI().createOrder(any())).thenReturn(orderCreateResponseMock1, orderCreateResponseMock2);
		
		strategy = new KucoinStrategy(restClientMock, publicWsClientMock, privateWsClientMock,
				BASE_TOKEN, QUOTE_TOKEN, 100, 100, PRICE_CHANGE_DELAY, producerTemplateMock);
		
	}
	
	/**
	 * When the application first starts, there are no orders for a given symbol and no balance
	 * for base currency. There is only balance for quote (usually USDT) currency, it should create only
	 * buy limit order for given amount of balance (e.g. 100 USDT combined).
	 */
	@Test
	public void noOrdersNoBaseBalanceShouldCreateBuyOrderOnly() throws Exception {
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 1000);
		
		strategy.run();
		
		timestamp = new Date().getTime();

		getCallbacks();
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("buy", "1.5", "66.6666", orderCreateRequest);
		
		publishAccounChangeEvent(QUOTE_TOKEN, 933.33);
		publishOrderChangeEvent("open", NEW_ORDER_ID1, orderCreateRequest);
	}

	/**
	 * When the application first starts, there are no orders for a given symbol and no balance
	 * at all so no orders can be created.
	 */
	@Test
	public void noOrdersNoBalanceShouldNotCreateAnyOrder() throws Exception {
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 0);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), never()).createOrder(any());
	}
	
	/**
	 * When the application starts and there are no orders for a given symbol and too big balance
	 * for base currency, it should create only sell orders with full size regardless set balance limit.
	 */
	@Test
	public void noOrdersBigBaseBalanceShouldCreateSellOrderOnly() throws Exception {
		addAccountBalancesResponse(BASE_TOKEN, 100);
		addAccountBalancesResponse(QUOTE_TOKEN, 1000);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("sell", "2.4", "100", orderCreateRequest);
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishOrderChangeEvent("open", NEW_ORDER_ID1, orderCreateRequest);
	}
	
	/**
	 * When there are orders on right price levels and no available base balance, do nothing.
	 */
	@Test
	public void oneOrderEachSideCorrectLevelsAndNoBaseBalanceShouldDoNothing() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 30, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), never()).createOrder(any());
	}

	/**
	 * When there are orders on right price levels but available base balance, create additional sell order.
	 */
	@Test
	public void oneOrderEachSideCorrectLevelsButBaseBalanceShouldCreateAdditionalSellOrder() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 10);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("sell", "2.4", "10", orderCreateRequest);
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishOrderChangeEvent("open", NEW_ORDER_ID1, orderCreateRequest);
	}
	
	/**
	 * When the price moves and orders become on not correct price levels it should cancel them
	 * and create new ones.
	 * Note: multiple orders either on same or different level goes through the same program path
	 */
	@Test
	public void oneOrderEachSidePriceMovesShouldRecreateOrders() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		when(restClientMock.orderAPI().cancelOrder(eq("orderBuy"))).thenAnswer(i -> {
			publishOrderChangeEvent("cancelled", "orderBuy", SYMBOL);
			publishAccounChangeEvent(QUOTE_TOKEN, 55);
			return null;
		});
		when(restClientMock.orderAPI().cancelOrder(eq("orderSell"))).thenAnswer(i -> {
			publishOrderChangeEvent("cancelled", "orderSell", SYMBOL);
			publishAccounChangeEvent(BASE_TOKEN, 20);
			return null;
		});
			
		publishTestOrderBookMove();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBookMove();
		
		verify(restClientMock.orderAPI(), times(1)).cancelOrder(eq("orderBuy"));
		verify(restClientMock.orderAPI(), times(1)).cancelOrder(eq("orderSell"));
		verify(restClientMock.orderAPI(), times(2)).createOrder(orderCreateRequestCaptor.capture());
		
		
		List<OrderCreateApiRequest> orderCreateRequests = orderCreateRequestCaptor.getAllValues();
		assertOrder("sell", "2.5", "20", orderCreateRequests.get(0));
		assertOrder("buy", "1.6", "31.2500", orderCreateRequests.get(1));
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishAccounChangeEvent(QUOTE_TOKEN, 0);
		publishOrderChangeEvent("open", NEW_ORDER_ID1, orderCreateRequests.get(0));
		publishOrderChangeEvent("open", NEW_ORDER_ID2, orderCreateRequests.get(1));
	}

	private void publishTestOrderBook() throws InterruptedException {
		publishL2Event(new Double[][] {{2.1, 5.0}, {2.2, 10.0}, {2.3, 18.0}, {2.4, 20.0}, {2.5, 30.0}, {2.6, 40.0}},
				new Double[][] {{1.9, 3.0}, {1.8, 5.0}, {1.7, 14.0}, {1.6, 20.0}, {1.5, 50.0}, {1.4, 80.0}});
		
		// XXX How to make it better?
		Thread.sleep(1000);
	}
	
	private void publishTestOrderBookMove() throws InterruptedException {
		publishL2Event(new Double[][] {{2.2, 5.0}, {2.3, 10.0}, {2.4, 18.0}, {2.5, 20.0}, {2.6, 30.0}, {2.7, 40.0}},
				new Double[][] {{2.0, 3.0}, {1.9, 5.0}, {1.8, 14.0}, {1.7, 20.0}, {1.6, 50.0}, {1.5, 80.0}});
		
		// XXX How to make it better?
		Thread.sleep(1000);
	}

	/**
	 * When the price moves and coming back within price change delay it should do nothing and keep orders as they are.
	 * Note: multiple orders either on same or different level goes through the same program path
	 */
	@Test
	public void oneOrderEachSidePriceMovesUpAndBackShouldDoNothing() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		when(restClientMock.orderAPI().cancelOrder(eq("orderBuy"))).thenAnswer(i -> {
			publishOrderChangeEvent("cancelled", "orderBuy", SYMBOL);
			publishAccounChangeEvent(QUOTE_TOKEN, 55);
			return null;
		});
		when(restClientMock.orderAPI().cancelOrder(eq("orderSell"))).thenAnswer(i -> {
			publishOrderChangeEvent("cancelled", "orderSell", SYMBOL);
			publishAccounChangeEvent(BASE_TOKEN, 20);
			return null;
		});
		
		publishTestOrderBookMove();
		timestamp += (PRICE_CHANGE_DELAY / 2);
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), never()).createOrder(any());
	}
	
	/**
	 * One order on each side. Buy order filled it should create a new sell order. No buy order
	 * as balance to use is exceeded.
	 */
	@Test
	public void oneOrderEachBuySideFilledShouldCreateNewSell() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();

		publishOrderChangeEvent("filled", "orderBuy", SYMBOL);
		publishAccounChangeEvent(BASE_TOKEN, 30);
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		assertOrder("sell", "2.4", "30", orderCreateRequest);
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishOrderChangeEvent("open", "newSellOrder", orderCreateRequest);
	}

	/**
	 * One order on each side. Sell order filled it should create a new buy order. No sell order
	 * as balance to use is exceeded.
	 */
	@Test
	public void oneOrderEachSideSellFilledShouldCreateNewBuy() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 30, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		publishOrderChangeEvent("filled", "orderSell", SYMBOL);
		publishAccounChangeEvent(QUOTE_TOKEN, 58);
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		assertOrder("buy", "1.5", "36.6666", orderCreateRequest);
		
		publishAccounChangeEvent(QUOTE_TOKEN, 10);
		publishOrderChangeEvent("open", "newSellOrder", orderCreateRequest);
	}
	
	/**
	 * One order on each side. Buy order partially filled it should create a new sell order. The remaining
	 * buy order keeps open.
	 */
	@Test
	public void oneOrderEachBuySidePartialFilledShouldCreateNewSell() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		publishOrderChangeEvent("match", "orderBuy", SYMBOL, null, null, null, new BigDecimal("15"));
		publishAccounChangeEvent(BASE_TOKEN, 15);
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		assertOrder("sell", "2.4", "15", orderCreateRequest);
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishOrderChangeEvent("open", "newSellOrder", orderCreateRequest);
	}
	
	/**
	 * One order on each side. Sell order partially filled it should create a new buy order. The remaining
	 * sell order keeps open.
	 */
	@Test
	public void oneOrderEachSideSellPartialFilledShouldCreateNewBuy() throws Exception {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 30, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		publishOrderChangeEvent("match", "orderSell", SYMBOL, null, null, null, new BigDecimal("15"));
		publishAccounChangeEvent(QUOTE_TOKEN, 46);
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		assertOrder("buy", "1.5", "12.6666", orderCreateRequest);
		
		publishAccounChangeEvent(QUOTE_TOKEN, 27);
		publishOrderChangeEvent("open", "newSellOrder", orderCreateRequest);
	}

	/**
	 * #5 bug placing more orders than maximum allowed, up to twice, because of asynchronous nature
	 * when base balance = 0 change came between placing sell orders and calculating available
	 * balance for buy orders what caused the calculation to think no sell orders are open.
	 */
	@Test
	public void noOrders() throws Exception {
		addAccountBalancesResponse(BASE_TOKEN, 70);
		addAccountBalancesResponse(QUOTE_TOKEN, 1000);
		
		strategy.run();
		
		timestamp = new Date().getTime();
		
		getCallbacks();
		
		when(restClientMock.orderAPI().createOrder(any())).thenAnswer(i -> {
			publishAccounChangeEvent(BASE_TOKEN, 0);
			return orderCreateResponseMock1;
		});
		
		// To push through the delay 
		publishTestOrderBook();
		timestamp += PRICE_CHANGE_DELAY;
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("sell", "2.4", "70", orderCreateRequest);
		publishOrderChangeEvent("open", NEW_ORDER_ID1, orderCreateRequest);
	}
	
	private void assertOrder(String side, String price, String size, OrderCreateApiRequest orderCreateRequest) {
		Assert.assertEquals(side, orderCreateRequest.getSide());
		Assert.assertEquals(new BigDecimal(price), orderCreateRequest.getPrice());
		Assert.assertEquals(new BigDecimal(size), orderCreateRequest.getSize());
		
	}

	private void getCallbacks() {
		verify(publicWsClientMock).onLevel2Data(anyInt(), l2EventCallbackCaptor.capture(), anyString());
		l2EventCallback = l2EventCallbackCaptor.getValue();
		
		verify(privateWsClientMock).onAccountBalance(accountChangeEventCallbackCaptor.capture());
		accountChangeEventCallback = accountChangeEventCallbackCaptor.getValue();
		
		verify(privateWsClientMock).onOrderChange(orderChangeEventCallbackCaptor.capture());
		orderChangeEventCallback = orderChangeEventCallbackCaptor.getValue();
	}
	
	private void publishL2Event(Double[][] asks, Double[][] bids) {
		KucoinEvent<Level2Event> event = new KucoinEvent<>();
		Level2Event data = new Level2Event();
		event.setData(data);
		data.setTimestamp(timestamp++);
		data.setAsks(toObjects(asks));
		data.setBids(toObjects(bids));
		l2EventCallback.onResponse(event);
	}
	
	private Object[][] toObjects(Double[][] array) {
		return Arrays.stream(array).map(e -> new Object[] {e[0].toString(), e[1].toString()}).toArray(Object[][]::new);
	}

	private void publishOrderChangeEvent(String type, String id, OrderCreateApiRequest request) {
		publishOrderChangeEvent(type, id, request.getSymbol(), request.getSide(), request.getSize(),
				request.getPrice());
	}

	private void publishOrderChangeEvent(String type, String id, String symbol, String side, BigDecimal size,
			BigDecimal price) {
		publishOrderChangeEvent(type, id, symbol, side, size, price, null);
	}
	
	private void publishOrderChangeEvent(String type, String id, String symbol) {
		publishOrderChangeEvent(type, id, symbol, null, null, null);
	}

	private void publishOrderChangeEvent(String type, String id, String symbol, String side, BigDecimal size,
			BigDecimal price, BigDecimal remainingSize) {
		KucoinEvent<OrderChangeEvent> event = new KucoinEvent<>();
		OrderChangeEvent data = new OrderChangeEvent();
		event.setData(data);
		data.setType(type);
		data.setOrderId(id);
		data.setOrderTime(timestamp++);
		data.setSymbol(symbol);
		data.setSide(side);
		data.setSize(size);
		data.setPrice(price);
		data.setRemainSize(remainingSize);
		orderChangeEventCallback.onResponse(event);
	}
	
	private void publishAccounChangeEvent(String currency, double available) {
		KucoinEvent<AccountChangeEvent> event = new KucoinEvent<>();
		AccountChangeEvent data = new AccountChangeEvent();
		event.setData(data);
		data.setCurrency(currency);
		data.setAvailable(new BigDecimal(available));
		data.setTime("" + timestamp++);
		accountChangeEventCallback.onResponse(event);
	}
	
	private void addOrderResponse(String id, String symbol, String side, double size, String price) {
		OrderResponse order = new OrderResponse();
		order.setId(id);
		order.setSymbol(symbol);
		order.setSide(side);
		order.setSize(new BigDecimal(size));
		order.setPrice(new BigDecimal(price));
		orderResponses.add(order);
	}
	
	private void addAccountBalancesResponse(String currency, double available) {
		AccountBalancesResponse balance = new AccountBalancesResponse();
		balance.setCurrency(currency);
		balance.setAvailable(new BigDecimal(available));
		accountBalanceResponses.add(balance);
	}
}
