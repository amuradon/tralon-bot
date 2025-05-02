package cz.amuradon.tralon.agent.connector.mexc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import cz.amuradon.tralon.agent.connector.InvalidPrice;
import cz.amuradon.tralon.agent.connector.NewOrderError;
import cz.amuradon.tralon.agent.connector.NewOrderResponse;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.StatusType;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MexcRestClientAdapterTest {

	private static final String SYMBOL = "TKNUSDT";

	@Mock
	private MexcClient mexcClientMock;
	
	private MexcRestClientAdapter client;
	
	private Map<String, Integer> quantityScales;
	
	private Map<String, Integer> priceScales;
	
	@BeforeEach
	public void prepare() {
		quantityScales = new HashMap<>();
		priceScales = new HashMap<>();
		
		client = new MexcRestClientAdapter("secretKey", mexcClientMock, new ObjectMapper(), quantityScales, priceScales);
	}
	
	@ParameterizedTest
	@MethodSource("errorResponsePriceData")
	public void errorResponsePrice(String errorMessage, BigDecimal expectedMaxPrice) throws Exception {
		WebApplicationException webApplicationExceptionMock = mock(WebApplicationException.class);
		Response responseMock = mock(Response.class);
		StatusType statusTypeMock = mock(StatusType.class);
		
		when(webApplicationExceptionMock.getResponse()).thenReturn(responseMock);
		when(responseMock.getStatus()).thenReturn(400);
		ErrorResponse errorResponse = new ErrorResponse("30010", errorMessage);
		when(responseMock.readEntity(ErrorResponse.class))
				.thenReturn(errorResponse);
		when(responseMock.getStatusInfo()).thenReturn(statusTypeMock);
		when(statusTypeMock.getReasonPhrase()).thenReturn("Bad Request");
		doThrow(webApplicationExceptionMock).when(mexcClientMock).newOrder(anyMap());
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		NewOrderResponse response = client.newOrder().symbol(SYMBOL).price(BigDecimal.ONE).size(BigDecimal.ONE).send();
		
		Assertions.assertFalse(response.success());
		Assertions.assertNull(response.orderId());
		
		NewOrderError error = response.error();
		Assertions.assertInstanceOf(InvalidPrice.class, error);
		
		InvalidPrice ipError = (InvalidPrice) error;
		Assertions.assertEquals(expectedMaxPrice, ipError.validPrice());
	}
	
	static Stream<Arguments> errorResponsePriceData() {
		return Stream.of(
				Arguments.of("Order price cannot exceed 5USDT", new BigDecimal("5")),
				Arguments.of("Order price cannot exceed 0.05USDT", new BigDecimal("0.05"))
		);
	}
}
