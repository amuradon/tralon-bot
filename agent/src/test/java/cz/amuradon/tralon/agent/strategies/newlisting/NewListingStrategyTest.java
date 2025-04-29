package cz.amuradon.tralon.agent.strategies.newlisting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cz.amuradon.tralon.agent.connector.InvalidPrice;
import cz.amuradon.tralon.agent.connector.NewOrderError;
import cz.amuradon.tralon.agent.connector.NewOrderResponse;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.StatusType;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NewListingStrategyTest {

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
	private ScheduledExecutorService scheduledExecutorServiceMock;
	
	@Mock
	private ScheduledFuture<?> scheduledFutureMock;
	
	@Captor
	private ArgumentCaptor<BigDecimal> priceCaptor;
	
	private NewListingStrategy strategy;
	
	@BeforeEach
	public void prepare() {
		when(restClientMock.newOrder()).thenReturn(newOrderBuilderMock);
		when(scheduledExecutorServiceMock.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
			.thenAnswer(i -> {
				i.getArgument(0, Runnable.class).run();
				return scheduledFutureMock;
			});
		when(computeInitialPriceMock.execute(anyString(), any())).thenReturn(new BigDecimal("0.1"));
		
		strategy = new NewListingStrategy(scheduledExecutorServiceMock, restClientMock, websocketClientMock,
				computeInitialPriceMock, new BigDecimal(100), "TKNUSDT", LocalDateTime.now().plusMinutes(5), 5, 5, 15, 500, 500);
	}

	@Test
	public void testSuccessfulSend() throws Exception {
		when(newOrderBuilderMock.send()).thenReturn(new NewOrderResponse(true, "orderId", null));
		strategy.start();
		verify(newOrderBuilderMock).send();
	}

	@Test
	public void invalidPrice() throws Exception {
		mockNewOrderException(400, (e, r) -> new InvalidPrice(e, new BigDecimal("0.05"), r));
		
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
		mockNewOrderException(429, (e, r) -> new NewOrderError(e, r));
		
		strategy.start();
		
		verify(newOrderBuilderMock).price(priceCaptor.capture());
		verify(newOrderBuilderMock, times(2)).send();
	}
	
	@Test
	public void tradeDirectionNotAllowed() throws Exception {
		mockNewOrderException(429, (e, r) -> new NewOrderError(e, r));
		
		strategy.start();
		
		verify(newOrderBuilderMock).price(priceCaptor.capture());
		verify(newOrderBuilderMock, times(2)).send();
	}

	private void mockNewOrderException(int status, BiFunction<WebApplicationException, ErrorResponse, NewOrderError> errorFactory) {
		WebApplicationException webApplicationExceptionMock = mock(WebApplicationException.class);
		Response responseMock = mock(Response.class);
		StatusType statusTypeMock = mock(StatusType.class);
		
		when(webApplicationExceptionMock.getResponse()).thenReturn(responseMock);
		when(responseMock.getStatus()).thenReturn(status);
		ErrorResponse errorResponse = new ErrorResponse("1111", "some error message");
		when(responseMock.getStatusInfo()).thenReturn(statusTypeMock);
		when(statusTypeMock.getReasonPhrase()).thenReturn("Bad Request");
		when(newOrderBuilderMock.send()).thenReturn(new NewOrderResponse(false, null,
				errorFactory.apply(webApplicationExceptionMock, errorResponse)),
				new NewOrderResponse(true, "orderId", null));
	}
	
}
