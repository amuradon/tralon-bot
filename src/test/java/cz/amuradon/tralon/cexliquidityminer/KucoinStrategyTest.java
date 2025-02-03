package cz.amuradon.tralon.cexliquidityminer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.rest.response.OrderResponse;
import com.kucoin.sdk.websocket.KucoinAPICallback;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;
import com.kucoin.sdk.websocket.event.KucoinEvent;
import com.kucoin.sdk.websocket.event.Level2Event;
import com.kucoin.sdk.websocket.event.OrderChangeEvent;

@ExtendWith(MockitoExtension.class)
public class KucoinStrategyTest {

	private static final String QUOTE_TOKEN = "USDT";

	private static final String BASE_TOKEN = "TKN";
	
	private static final String SYMBOL = BASE_TOKEN + "-" + QUOTE_TOKEN;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private KucoinRestClient restClientMock;
	
	@Mock
	private KucoinPrivateWSClient privateWsClientMock;
	
	@Mock
	private KucoinPublicWSClient publicWsClientMock;
	
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
		Mockito.when(restClientMock.orderAPI().listOrders(anyString(), any(), any(), any(),
				anyString(), any(), any(), anyInt(), anyInt()).getItems()).thenReturn(orderResponses);
		
		accountBalanceResponses = new ArrayList<>();
		Mockito.when(restClientMock.accountAPI().listAccounts(null, "trade")).thenReturn(accountBalanceResponses);
		
		strategy = new KucoinStrategy(restClientMock, publicWsClientMock, privateWsClientMock,
				BASE_TOKEN, QUOTE_TOKEN, 100, 100);
		
		timestamp = new Date().getTime();
	}
	
	/*
	 * - no existing orders
	 * - existing orders on right level
	 * - existing orders on farther from price level - ask / bid
	 * - existing orders on closer to price level - ask / bid
	 * - one order - ask or bid
	 * - one order on each side
	 * - multiple orders on same level
	 * - multiple orders on different levels
	 * - partial fill
	 * - multiple strategies
	 * - zero balance
	 * - not enough balance - base / quote
	 */
	
	/**
	 * When the application first starts, there are no orders for a given symbol and no balance
	 * for base currency. There is only balance for quote (usually USDT) currency, it should create only
	 * buy limit order for given amount of balance (e.g. 100 USDT combined).
	 */
	@Test
	public void noOrdersNoBaseBalanceShouldCreateBuyOrderOnly() throws IOException {
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 1000);
		
		strategy.run();
		
		getCallbacks();
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), times(0)).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("buy", "1.5", "66.666666", orderCreateRequest);
		
		publishAccounChangeEvent(QUOTE_TOKEN, 900);
		publishOrderChangeEvent("open", "newOrder1", orderCreateRequest);
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
	}

	private void publishTestOrderBook() {
		publishL2Event(new Double[][] {{2.1, 5.0}, {2.2, 10.0}, {2.3, 18.0}, {2.4, 20.0}, {2.5, 30.0}, {2.6, 40.0}},
				new Double[][] {{1.9, 3.0}, {1.8, 5.0}, {1.7, 14.0}, {1.6, 20.0}, {1.5, 50.0}, {1.4, 80.0}});
	}

	/**
	 * When the application first starts, there are no orders for a given symbol and no balance
	 * at all so no orders can be created.
	 */
	@Test
	public void noOrdersNoBalanceShouldNotCreateAnyOrder() throws IOException {
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 0);
		
		strategy.run();
		
		getCallbacks();
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), never()).createOrder(any());
	}
	
	/**
	 * When the application starts and there are no orders for a given symbol and too big balance
	 * for base currency, it should create only sell orders with full size regardless set balance limit.
	 */
	@Test
	public void noOrdersBigBaseBalanceShouldCreateSellOrderOnly() throws IOException {
		addAccountBalancesResponse(BASE_TOKEN, 100);
		addAccountBalancesResponse(QUOTE_TOKEN, 1000);
		
		strategy.run();
		
		getCallbacks();
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("sell", "2.4", "100", orderCreateRequest);
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishOrderChangeEvent("open", "newOrder1", orderCreateRequest);
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
	}
	
	/**
	 * When there are orders on right price levels and no available base balance, do nothing.
	 */
	@Test
	public void oneOrderEachSideCorrectLevelsAndNoBaseBalanceShouldDoNothing() throws IOException {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		getCallbacks();
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), never()).createOrder(any());
	}

	/**
	 * When there are orders on right price levels but available base balance, create additional sell order.
	 */
	@Test
	public void oneOrderEachSideCorrectLevelsButBaseBalanceShouldCreateAdditionalSellOrder() throws IOException {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 10);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		getCallbacks();
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
		
		OrderCreateApiRequest orderCreateRequest = orderCreateRequestCaptor.getValue();
		
		assertOrder("sell", "2.4", "10", orderCreateRequest);
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishOrderChangeEvent("open", "newOrder1", orderCreateRequest);
		
		publishTestOrderBook();
		
		verify(restClientMock.orderAPI(), never()).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(1)).createOrder(orderCreateRequestCaptor.capture());
	}
	
	/**
	 * When the price moves and orders become on not correct price levels it should cancel them
	 * and create new ones.
	 */
	@Test
	public void oneOrderEachSidePriceMovesShouldRecreateOrders() throws IOException {
		addOrderResponse("orderBuy", SYMBOL, "buy", 30, "1.5");
		addOrderResponse("orderSell", SYMBOL, "sell", 20, "2.4");
		
		addAccountBalancesResponse(BASE_TOKEN, 0);
		addAccountBalancesResponse(QUOTE_TOKEN, 10);
		
		strategy.run();
		
		getCallbacks();
		
		publishL2Event(new Double[][] {{2.2, 5.0}, {2.3, 10.0}, {2.4, 18.0}, {2.5, 20.0}, {2.6, 30.0}, {2.7, 40.0}},
				new Double[][] {{2.0, 3.0}, {1.9, 5.0}, {1.8, 14.0}, {1.7, 20.0}, {1.6, 50.0}, {1.5, 80.0}});
		
		verify(restClientMock.orderAPI(), times(1)).cancelOrder(eq("orderBuy"));
		verify(restClientMock.orderAPI(), times(1)).cancelOrder(eq("orderSell"));
		verify(restClientMock.orderAPI(), never()).createOrder(any());
		
		// Only ID is processed for cancel
		publishOrderChangeEvent("cancelled", "orderBuy", null, null, null, null);
		publishOrderChangeEvent("cancelled", "orderSell", null, null, null, null);
		publishAccounChangeEvent(BASE_TOKEN, 20);
		publishAccounChangeEvent(QUOTE_TOKEN, 55);
		
		publishL2Event(new Double[][] {{2.2, 5.0}, {2.3, 10.0}, {2.4, 18.0}, {2.5, 20.0}, {2.6, 30.0}, {2.7, 40.0}},
				new Double[][] {{2.0, 3.0}, {1.9, 5.0}, {1.8, 14.0}, {1.7, 20.0}, {1.6, 50.0}, {1.5, 80.0}});
		
		// The cancel calls above ^^^
		verify(restClientMock.orderAPI(), times(2)).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(2)).createOrder(orderCreateRequestCaptor.capture());
		
		List<OrderCreateApiRequest> orderCreateRequests = orderCreateRequestCaptor.getAllValues();
		assertOrder("sell", "2.5", "20", orderCreateRequests.get(0));
		assertOrder("buy", "1.6", "3.125000", orderCreateRequests.get(1));
		
		publishAccounChangeEvent(BASE_TOKEN, 0);
		publishAccounChangeEvent(QUOTE_TOKEN, 0);
		publishOrderChangeEvent("open", "newOrder1", orderCreateRequests.get(0));
		publishOrderChangeEvent("open", "newOrder2", orderCreateRequests.get(1));
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
		KucoinEvent<OrderChangeEvent> event = new KucoinEvent<>();
		OrderChangeEvent data = new OrderChangeEvent();
		event.setData(data);
		data.setType(type);
		data.setOrderId(id);
		data.setTs(timestamp++);
		data.setSymbol(symbol);
		data.setSide(side);
		data.setSize(size);
		data.setPrice(price);
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
