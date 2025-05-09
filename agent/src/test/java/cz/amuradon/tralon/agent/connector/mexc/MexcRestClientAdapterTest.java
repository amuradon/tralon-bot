package cz.amuradon.tralon.agent.connector.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.InvalidPrice;
import cz.amuradon.tralon.agent.connector.NewOrderError;
import cz.amuradon.tralon.agent.connector.NewOrderResponse;
import cz.amuradon.tralon.agent.connector.TradeDirectionNotAllowed;
import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.StatusType;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MexcRestClientAdapterTest {

	private static final String SECRET_KEY = "51c13954c8e043cd9215b9f32c8eaf86";

	private static final String ORDER_ID = "orderId";

	private static final String SYMBOL = "TKNUSDT";

	@Mock
	private MexcClient mexcClientMock;
	
	private MexcRestClientAdapter client;
	
	private Map<String, Integer> quantityScales;
	
	private Map<String, Integer> priceScales;
	
	@Captor
	private ArgumentCaptor<Map<String, Object>> parametersCaptor;
	
	@BeforeEach
	public void prepare() {
		quantityScales = new HashMap<>();
		priceScales = new HashMap<>();
		
		client = new MexcRestClientAdapter(SECRET_KEY,
				mexcClientMock, new ObjectMapper(), quantityScales, priceScales);
	}
	
	static Stream<Arguments> newOrderInvalidPriceData() {
		return Stream.of(
				Arguments.of("Order price cannot exceed 5USDT", new BigDecimal("5")),
				Arguments.of("Order price cannot exceed 0.05USDT", new BigDecimal("0.05"))
		);
	}
	
	@ParameterizedTest
	@MethodSource("newOrderInvalidPriceData")
	public void newOrderInvalidPrice(String errorMessage, BigDecimal expectedMaxPrice) throws Exception {
		NewOrderError error = testErrorResponse(errorMessage, 400, "30010", InvalidPrice.class);
		
		InvalidPrice ipError = (InvalidPrice) error;
		Assertions.assertEquals(expectedMaxPrice, ipError.validPrice());
	}
	
	
	static Stream<Arguments> newOrderOtherErrorResponsesData() {
		return Stream.of(
				Arguments.of("Not important here", 429, "Not important here", NewOrderError.class),
				Arguments.of("Not important here", 400, "30001", TradeDirectionNotAllowed.class)
				);
	}
	
	@ParameterizedTest
	@MethodSource("newOrderOtherErrorResponsesData")
	public void newOrderOtherErrorResponses(String errorMessage, int status, String errorCode,
			Class<?> errorClass) throws Exception {
		testErrorResponse(errorMessage, status, errorCode, errorClass);
	}
	
	private NewOrderError testErrorResponse(String errorMessage, int status, String errorCode,
			Class<?> errorClass) throws Exception {
		WebApplicationException webApplicationExceptionMock = mock(WebApplicationException.class);
		Response responseMock = mock(Response.class);
		StatusType statusTypeMock = mock(StatusType.class);
		
		when(webApplicationExceptionMock.getResponse()).thenReturn(responseMock);
		when(responseMock.getStatus()).thenReturn(status);
		ErrorResponse errorResponse = new ErrorResponse(errorCode, errorMessage);
		when(responseMock.readEntity(ErrorResponse.class))
		.thenReturn(errorResponse);
		when(responseMock.getStatusInfo()).thenReturn(statusTypeMock);
		when(statusTypeMock.getReasonPhrase()).thenReturn("Not important");
		doThrow(webApplicationExceptionMock).when(mexcClientMock).newOrder(anyMap());
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		NewOrderResponse response = client.newOrder().symbol(SYMBOL).price(BigDecimal.ONE).size(BigDecimal.ONE).send();
		
		Assertions.assertFalse(response.success());
		Assertions.assertNull(response.orderId());
		
		NewOrderError error = response.error();
		Assertions.assertInstanceOf(errorClass, error);
		return error;
	}
	
	@Test
	public void newOrderMissingSizeShouldThrowException() {
		assertThrows(IllegalArgumentException.class, () ->
			client.newOrder().symbol(SYMBOL).price(BigDecimal.ONE).send());
	}

	@Test
	public void newOrderMissingPriceShouldThrowException() {
		assertThrows(IllegalArgumentException.class, () ->
			client.newOrder().symbol(SYMBOL).size(BigDecimal.ONE).send());
	}

	@Test
	public void newOrderShouldContainAllParams() {
		long timestamp = 1746446247L;
		String clientOrderId = "clientOrderId";
		
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		NewOrderResponse response = client.newOrder().symbol(SYMBOL).clientOrderId(clientOrderId)
				.size(new BigDecimal("1.22222")).side(Side.BUY)
				.price(new BigDecimal("2.11111")).timestamp(timestamp).recvWindow(10000).type(OrderType.LIMIT)
				.signParams().send();
		
		assertTrue(response.success());
		assertEquals(ORDER_ID, response.orderId());
		assertNull(response.error());
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertEquals(SYMBOL, parameters.get("symbol"));
		assertEquals(clientOrderId, parameters.get("newClientOrderId"));
		assertEquals("BUY", parameters.get("side"));
		assertEquals("1.2222", parameters.get("quantity"));
		assertEquals("2.1111", parameters.get("price"));
		assertEquals(String.valueOf(timestamp), parameters.get("timestamp"));
		assertEquals(String.valueOf(10000), parameters.get("recvWindow"));
		assertEquals("LIMIT", parameters.get("type"));
		assertEquals("08683ef1fd551992d00d52d0ab7f81ebbc04235341498bf5a32d5c1b35da4f0c", parameters.get("signature"));
	}
	
	@Test
	public void newOrderNoTimestampShouldFillIt() {
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		NewOrderResponse response = client.newOrder().symbol(SYMBOL)
				.size(BigDecimal.ONE).side(Side.BUY)
				.price(BigDecimal.ONE).type(OrderType.LIMIT)
				.signParams().send();
		
		assertTrue(response.success());
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertNotNull(parameters.get("timestamp"));
	}

	@Test
	public void newOrderNotSignedShouldSign() {
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		NewOrderResponse response = client.newOrder().symbol(SYMBOL)
				.size(BigDecimal.ONE).side(Side.BUY)
				.price(BigDecimal.ONE).type(OrderType.LIMIT)
				.send();
		
		assertTrue(response.success());
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertNotNull(parameters.get("signature"));
	}

	// TODO vice ruznych testu na signature?
	@Test
	public void newOrderSignature() throws Exception {
		final String hmac = "HmacSHA256";
		Mac mac = Mac.getInstance(hmac);
		mac.init(new SecretKeySpec(SECRET_KEY.getBytes(), hmac));
		
		String toBeSigned = "symbol=TKNUSDT&quantity=1.0044&side=BUY&price=2.1155&type=LIMIT&timestamp=1746446247";
    	String signature = HexFormat.of().formatHex(mac.doFinal(toBeSigned.getBytes()));
		
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		NewOrderResponse response = client.newOrder().symbol(SYMBOL)
				.size(new BigDecimal("1.00441")).side(Side.BUY)
				.price(new BigDecimal("2.11551")).type(OrderType.LIMIT)
				.timestamp(1746446247L)
				.signParams()
				.send();
		
		assertTrue(response.success());
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertEquals(signature, parameters.get("signature"));
	}

}
