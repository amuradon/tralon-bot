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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
	private RestClient.NewOrderBuilder newOrderRequestBuilderMock;
	
	@Mock
	private ScheduledExecutorService scheduledExecutorServiceMock;
	
	@Mock
	private ScheduledFuture<?> scheduledFutureMock;
	
	private NewListingStrategy strategy;
	
	@BeforeEach
	public void prepare() {
		when(restClientMock.newOrder()).thenReturn(newOrderRequestBuilderMock);
		when(newOrderRequestBuilderMock.send()).thenReturn(new NewOrderResponse(true, "orderId", null));
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
		strategy.start();
		verify(newOrderRequestBuilderMock).send();
	}

	@ParameterizedTest
	@MethodSource("errorResponsePriceData")
	public void errorResponsePrice(String errorMessage, String expectedMaxPrice) throws Exception {
		WebApplicationException webApplicationExceptionMock = mock(WebApplicationException.class);
		Response responseMock = mock(Response.class);
		StatusType statusTypeMock = mock(StatusType.class);
		
		when(webApplicationExceptionMock.getResponse()).thenReturn(responseMock);
		when(responseMock.getStatus()).thenReturn(400);
		when(responseMock.readEntity(ErrorResponse.class))
				.thenReturn(new ErrorResponse("30010", errorMessage));
		when(responseMock.getStatusInfo()).thenReturn(statusTypeMock);
		when(statusTypeMock.getReasonPhrase()).thenReturn("Bad Request");
		
		doThrow(webApplicationExceptionMock).when(newOrderRequestBuilderMock).send();
		strategy.start();
		
		// XXX zatim nemam, ze druhy pokus je success...
		verify(newOrderRequestBuilderMock, times(2)).price(new BigDecimal(expectedMaxPrice));
	}
	
	static Stream<Arguments> errorResponsePriceData() {
		return Stream.of(
				Arguments.of("Order price cannot exceed 5USDT", "5"),
				Arguments.of("Order price cannot exceed 0.05USDT", "0.05")
		);
	}
	
	/*
	 * TODO
	 * - testovat opakovani posilani
	 */
	
}
