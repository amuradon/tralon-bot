package cz.amuradon.tralon.agent.strategies.newlisting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
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

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.connector.NoValidTradePriceException;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RequestException;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.SymbolInfo;
import cz.amuradon.tralon.agent.connector.Trade;
import cz.amuradon.tralon.agent.connector.TradeDirectionNotAllowedException;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.binance.BinanaceTrade;
import cz.amuradon.tralon.agent.connector.binance.BinanceOrderChange;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.StatusType;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NewListingStrategyTest {

	private static final String SYMBOL = "TKNUSDT";

	@Mock
	private RestClient restClientMock;
	
	@Mock
	private WebsocketClient websocketClientMock;
	
	@Mock
	private ComputeInitialPrice computeInitialPriceMock;
	
	@Mock
	private Path dataPathMock;
	
	@Mock(answer = Answers.RETURNS_SELF)
	private RestClient.NewOrderBuilder newOrderBuilderMock;
	
	@Mock
	private RestClient.NewOrderSymbolBuilder newOrderSymbolBuilderMock;
	
	@Mock
	private ScheduledExecutorService scheduledExecutorServiceMock;
	
	@Mock
	private ScheduledFuture<?> scheduledFutureMock;
	
	@Captor
	private ArgumentCaptor<BigDecimal> priceCaptor;

	@Captor
	private ArgumentCaptor<String> clientOrderIdCaptor;
	
	@Captor
	private ArgumentCaptor<Consumer<Trade>> tradeCallbackCaptor;
	
	@Captor
	private ArgumentCaptor<Consumer<OrderChange>> orderChangeCallbackCaptor;

	@Captor
	private ArgumentCaptor<Runnable> schedulerTaskCaptor;
	
	private NewListingStrategy strategy;
	
	@BeforeEach
	public void prepare() {
		when(newOrderSymbolBuilderMock.symbol(anyString())).thenReturn(newOrderBuilderMock);
		when(restClientMock.cacheSymbolDetails(eq(SYMBOL))).thenReturn(new SymbolInfo(4));
		when(restClientMock.newOrder()).thenReturn(newOrderSymbolBuilderMock);
		when(computeInitialPriceMock.execute(anyString(), any())).thenReturn(new BigDecimal("0.1"));
		
		strategy = new NewListingStrategy(scheduledExecutorServiceMock, restClientMock, websocketClientMock,
				computeInitialPriceMock, new BigDecimal(100), SYMBOL, LocalDateTime.now().plusMinutes(5), 5, 5,
				new TrailingProfitStopUpdatesProcessor(restClientMock, SYMBOL, 15, 1, 500));
	}

	/*
	 * Testy:
	 * 1) buy order fully filled, cena stoupa, spadne pod stop price, proda
	 * 2) buy order castecne filled, cena stoupa pres max price, vytika, zrusi zbytek orderu, spadne pod stop price, proda koupenou cast
	 * 3) cena uleti prilis rychle, buy order neni filled, po vytikani se zrusi 
	 * - nereaguje na trade updaty, dokud neni otevrena pozice
	 */
	@Test
	public void fulfillAndSell() throws Exception {
		mockSchedulerTask(false);
		when(newOrderBuilderMock.send()).thenReturn("orderId");
		strategy.start();
		
		verify(scheduledExecutorServiceMock, times(2)).schedule(schedulerTaskCaptor.capture(), anyLong(), any());
		
		List<Runnable> tasks = schedulerTaskCaptor.getAllValues();
		Assertions.assertEquals(2, tasks.size());
		
		// They should be NewListingStrategy.prepare() and NewListingStrategy.placeNewOrder()
		Runnable prepare = tasks.get(0);
		Runnable placeNewOrder = tasks.get(1);
		
		prepare.run();
		
		verify(websocketClientMock).onTrade(tradeCallbackCaptor.capture(), anyString());
		verify(websocketClientMock).onOrderChange(orderChangeCallbackCaptor.capture());
		
		Consumer<Trade> onTrade = tradeCallbackCaptor.getValue();
		Consumer<OrderChange> onOrderChange = orderChangeCallbackCaptor.getValue();
		
		long timestamp = System.currentTimeMillis();
		
		// No order placed yet, so below trades makes only stop price calculation
		// Using BinanceTrade as simplest to create (record) - side and quantity don't matter as of now
		onTrade.accept(new BinanaceTrade(new BigDecimal("0.1"), null, null, timestamp));
		onTrade.accept(new BinanaceTrade(new BigDecimal("0.15"), null, null, timestamp + 1));
		placeNewOrder.run();
		
		verify(newOrderBuilderMock).send();
		verify(newOrderBuilderMock).clientOrderId(clientOrderIdCaptor.capture());
		
		// Position not opened yet, so below trades has no impact
		onTrade.accept(new BinanaceTrade(new BigDecimal("0.12"), null, null, timestamp + 2));
		onTrade.accept(new BinanaceTrade(new BigDecimal("0.11"), null, null, timestamp + 3));

		// ... no sell order sent
		verify(newOrderBuilderMock).send();
		
		// Using BinanceTrade as simplest to create (record) - null values don't matter as of now
		onOrderChange.accept(new BinanceOrderChange(OrderStatus.FILLED, null, null, clientOrderIdCaptor.getValue(),
				null, null, null, new BigDecimal("10")));
		
		// Max price was 0.15 and 15% trailing stop -> 0.1275
		onTrade.accept(new BinanaceTrade(new BigDecimal("0.12"), null, null, timestamp + 4));
		onTrade.accept(new BinanaceTrade(new BigDecimal("0.12"), null, null, timestamp + 6));
		
		verify(newOrderBuilderMock, times(2)).send();
		verify(newOrderBuilderMock).price(new BigDecimal("0.0001"));
	}

	@Test
	public void invalidPrice() throws Exception {
		mockSchedulerTask(true);
		mockNewOrderException(400, (r, er) -> new NoValidTradePriceException(r, new BigDecimal("0.05"), er));
		
		strategy.start();
		
		verify(newOrderBuilderMock, times(2)).price(priceCaptor.capture());
		verify(newOrderBuilderMock, times(2)).send();
		
		List<BigDecimal> prices = priceCaptor.getAllValues();
		Assertions.assertEquals(2, prices.size());
		Assertions.assertEquals(new BigDecimal("0.1"), prices.get(0));
		Assertions.assertEquals(new BigDecimal("0.05"), prices.get(1));
	}

	@Test
	public void tooManyRequests() throws Exception {
		mockSchedulerTask(true);
		mockNewOrderException(429, (r, er) -> new RequestException(r, er));
		
		strategy.start();
		
		verify(newOrderBuilderMock).price(priceCaptor.capture());
		verify(newOrderBuilderMock, times(2)).send();
	}
	
	@Test
	public void tradeDirectionNotAllowed() throws Exception {
		mockSchedulerTask(true);
		mockNewOrderException(400, (r, er) -> new TradeDirectionNotAllowedException(r, er));
		
		strategy.start();
		
		verify(newOrderBuilderMock).price(priceCaptor.capture());
		verify(newOrderBuilderMock, times(2)).send();
	}
	
	private void mockSchedulerTask(boolean runImmediately) {
		when(scheduledExecutorServiceMock.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
			.thenAnswer(i -> {
				if (runImmediately) {
					i.getArgument(0, Runnable.class).run();
				}
				return scheduledFutureMock;
			});
	}


	private void mockNewOrderException(int status, BiFunction<Response, ErrorResponse, RequestException> errorFactory) {
		WebApplicationException webApplicationExceptionMock = mock(WebApplicationException.class);
		Response responseMock = mock(Response.class);
		StatusType statusTypeMock = mock(StatusType.class);
		
		when(webApplicationExceptionMock.getResponse()).thenReturn(responseMock);
		when(responseMock.getStatus()).thenReturn(status);
		ErrorResponse errorResponse = new ErrorResponse("1111", "some error message");
		when(responseMock.getStatusInfo()).thenReturn(statusTypeMock);
		when(statusTypeMock.getReasonPhrase()).thenReturn("Bad Request");
		
		// The exception has to be initiated first and then passed to stub, otherwise the stubbing fails
		RequestException exception = errorFactory.apply(responseMock, errorResponse);
		when(newOrderBuilderMock.send()).thenThrow(exception).thenReturn("orderId");
	}
	
}
