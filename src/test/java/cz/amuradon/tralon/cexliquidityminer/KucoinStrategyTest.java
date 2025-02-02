package cz.amuradon.tralon.cexliquidityminer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
	
	private KucoinAPICallback<KucoinEvent<Level2Event>> l2EventCallback;
	
	private KucoinAPICallback<KucoinEvent<OrderChangeEvent>> orderChangeEventCallback;
	
	private KucoinAPICallback<KucoinEvent<AccountChangeEvent>> accountChangeEventCallback;
	
	private List<OrderResponse> orderResponses;
	
	private List<AccountBalancesResponse> accountBalanceResponses;
	
	@BeforeEach
	public void prepare() throws IOException {
		orderResponses = new ArrayList<>();
		Mockito.when(restClientMock.orderAPI().listOrders(anyString(), any(), any(), any(),
				anyString(), any(), any(), anyInt(), anyInt()).getItems()).thenReturn(orderResponses);
		
		accountBalanceResponses = new ArrayList<>();
		Mockito.when(restClientMock.accountAPI().listAccounts(null, "trade")).thenReturn(accountBalanceResponses);
	}
	
	/*
	 * - no existing orders
	 * - existing orders on right level
	 * - existing orders on farther from price level - ask / bid
	 * - existing orders on closer to price level - ask / bid
	 * - one order - ask or bid
	 * - one order on each side
	 * - multiple orders on same level
	 * - multiple orders on differenct levels
	 */
	
	@Test
	public void oneOrderEachSideCorrectLevelsShouldDoNothing() throws IOException {
		orderResponses.add(orderResponse("orderBuy", SYMBOL, "buy", 10, "1.5"));
		orderResponses.add(orderResponse("orderSell", SYMBOL, "sell", 10, "2.4"));
		
		accountBalanceResponses.add(accountBalancesResponse(BASE_TOKEN, 10));
		accountBalanceResponses.add(accountBalancesResponse(QUOTE_TOKEN, 10));
		
		KucoinStrategy strategy = new KucoinStrategy(restClientMock, publicWsClientMock, privateWsClientMock,
				BASE_TOKEN, QUOTE_TOKEN, 100);
		strategy.run();
		
		getCallbacks();
		
		publishL2Event(new Double[][] {{2.1, 5.0}, {2.2, 10.0}, {2.3, 18.0}, {2.4, 20.0}, {2.5, 30.0}, {2.6, 40.0}},
				new Double[][] {{1.9, 3.0}, {1.8, 5.0}, {1.7, 14.0}, {1.6, 20.0}, {1.5, 50.0}, {1.4, 80.0}});
		
		verify(restClientMock.orderAPI(), times(0)).cancelOrder(anyString());
		verify(restClientMock.orderAPI(), times(0)).createOrder(any());
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
		data.setTimestamp(new Date().getTime());
		data.setAsks(toObjects(asks));
		data.setBids(toObjects(bids));
		l2EventCallback.onResponse(event);
	}
	
	private Object[][] toObjects(Double[][] array) {
		return Arrays.stream(array).map(e -> new Object[] {e[0].toString(), e[1].toString()}).toArray(Object[][]::new);
	}

	private void publishOrderChangeEvent(String type, String id, String symbol, String side, double size, double price) {
		KucoinEvent<OrderChangeEvent> event = new KucoinEvent<>();
		OrderChangeEvent data = new OrderChangeEvent();
		event.setData(data);
		long now = new Date().getTime();
		data.setOrderId(id);
		data.setTs(now);
		data.setSymbol(symbol);
		data.setSide(side);
		data.setSize(new BigDecimal(size));
		data.setPrice(new BigDecimal(price));
		orderChangeEventCallback.onResponse(event);
	}
	
	private void publishAccounChangeEvent(String currency, double available) {
		KucoinEvent<AccountChangeEvent> event = new KucoinEvent<>();
		AccountChangeEvent data = new AccountChangeEvent();
		event.setData(data);
		data.setCurrency(currency);
		data.setAvailable(new BigDecimal(available));
		data.setTime("" + new Date().getTime());
		accountChangeEventCallback.onResponse(event);
	}
	
	private OrderResponse orderResponse(String id, String symbol, String side, double size, String price) {
		OrderResponse order = new OrderResponse();
		order.setId(id);
		order.setSymbol(symbol);
		order.setSide(side);
		order.setSize(new BigDecimal(size));
		order.setPrice(new BigDecimal(price));
		return order;
	}
	
	private AccountBalancesResponse accountBalancesResponse(String currency, double available) {
		AccountBalancesResponse balance = new AccountBalancesResponse();
		balance.setCurrency(currency);
		balance.setAvailable(new BigDecimal(available));
		return balance;
	}
}
