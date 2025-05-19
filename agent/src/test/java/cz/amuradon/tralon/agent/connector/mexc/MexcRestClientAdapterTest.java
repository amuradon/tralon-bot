package cz.amuradon.tralon.agent.connector.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.BiFunction;
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
import cz.amuradon.tralon.agent.connector.NoValidTradePriceException;
import cz.amuradon.tralon.agent.connector.RequestException;
import cz.amuradon.tralon.agent.connector.RestClient.NewOrderBuilder;
import cz.amuradon.tralon.agent.connector.TradeDirectionNotAllowedException;
import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
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
	public void newOrderNoValidPriceResponse(String errorMessage, BigDecimal expectedMaxPrice) throws Exception {
		RequestException error = testErrorResponse(errorMessage, 400, "30010", NoValidTradePriceException.class,
				(r, er) -> new NoValidTradePriceException(r, expectedMaxPrice, er));
		
		NoValidTradePriceException ipError = (NoValidTradePriceException) error;
		Assertions.assertEquals(expectedMaxPrice, ipError.validPrice());
	}
	
	@Test
	public void newOrderNoTradingYetResponse() throws Exception {
		testErrorResponse("Not important here", 400, "30001", TradeDirectionNotAllowedException.class,
				TradeDirectionNotAllowedException::new);
	}

	@Test
	public void newOrderTooManyRequestsResponses() throws Exception {
		testErrorResponse("Not important here", 429, "Not important here", RequestException.class,
				RequestException::new);
	}
	
	private <T extends Throwable> T testErrorResponse(String errorMessage, int status, String errorCode,
			Class<T> errorClass, BiFunction<Response, ErrorResponse, T> exceptionFactory) throws Exception {
		Response responseMock = mock(Response.class);
		StatusType statusTypeMock = mock(StatusType.class);
		
		when(responseMock.getStatus()).thenReturn(status);
		ErrorResponse errorResponse = new ErrorResponse(errorCode, errorMessage);
		when(responseMock.readEntity(ErrorResponse.class))
		.thenReturn(errorResponse);
		when(responseMock.getStatusInfo()).thenReturn(statusTypeMock);
		when(statusTypeMock.getReasonPhrase()).thenReturn("Not important");
		doThrow(exceptionFactory.apply(responseMock, errorResponse)).when(mexcClientMock).newOrder(anyMap());
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		
		return Assertions.assertThrows(errorClass, () -> client.newOrder().symbol(SYMBOL).price(BigDecimal.ONE).size(BigDecimal.ONE).send());
	}
	
	@Test
	public void newOrderShouldContainAllParams() {
		long timestamp = 1746446247L;
		String clientOrderId = "clientOrderId";
		
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		String orderId = client.newOrder().symbol(SYMBOL).clientOrderId(clientOrderId)
				.size(new BigDecimal("1.22222")).side(Side.BUY)
				.price(new BigDecimal("2.11111")).timestamp(timestamp).recvWindow(10000).type(OrderType.LIMIT)
				.signParams().send();
		
		assertEquals(ORDER_ID, orderId);
		
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
		assertEquals("7a0ade1dcee66333064b95efdaa55baf83741d35c4cd23b434f7d6ede964323b", parameters.get("signature"));
	}
	
	@Test
	public void newOrderNoTimestampShouldFillIt() {
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		String orderId = client.newOrder().symbol(SYMBOL)
				.size(BigDecimal.ONE).side(Side.BUY)
				.price(BigDecimal.ONE).type(OrderType.LIMIT)
				.signParams().send();
		
		assertEquals(ORDER_ID, orderId);
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertNotNull(parameters.get("timestamp"));
	}

	@Test
	public void newOrderNotSignedShouldSign() {
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		String orderId = client.newOrder().symbol(SYMBOL)
				.size(BigDecimal.ONE).side(Side.BUY)
				.price(BigDecimal.ONE).type(OrderType.LIMIT)
				.send();
		
		assertEquals(ORDER_ID, orderId);
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertNotNull(parameters.get("signature"));
	}

	// TODO vice ruznych testu na signature?
	@Test
	public void newOrderSignature() throws Exception {
		newOrderFirstSignature();
	}

	@Test
	public void newOrderRepeatedSignature() throws Exception {
		SignatureTestDataHolder holder = newOrderFirstSignature();
		
		holder.builder.price(new BigDecimal("2.22222")).timestamp(1746446347L).signParams().send();
		
		String toBeSigned = "symbol=TKNUSDT&quantity=1.0044&side=BUY&price=2.2222&type=LIMIT&timestamp=1746446347";
		Object signature = HexFormat.of().formatHex(holder.mac.doFinal(toBeSigned.getBytes()));
		
		verify(mexcClientMock, times(2)).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertEquals(signature, parameters.get("signature"));
	}
	
	private SignatureTestDataHolder newOrderFirstSignature() throws Exception {
		final String hmac = "HmacSHA256";
		Mac mac = Mac.getInstance(hmac);
		mac.init(new SecretKeySpec(SECRET_KEY.getBytes(), hmac));
		
		String toBeSigned = "symbol=TKNUSDT&quantity=1.0044&side=BUY&price=2.1155&type=LIMIT&timestamp=1746446247";
		String signature = HexFormat.of().formatHex(mac.doFinal(toBeSigned.getBytes()));
		
		when(mexcClientMock.newOrder(anyMap())).thenReturn(new OrderResponse(ORDER_ID));
		
		quantityScales.put(SYMBOL, 4);
		priceScales.put(SYMBOL, 4);
		NewOrderBuilder builder = client.newOrder().symbol(SYMBOL)
				.size(new BigDecimal("1.00441")).side(Side.BUY)
				.price(new BigDecimal("2.11551")).type(OrderType.LIMIT)
				.timestamp(1746446247L)
				.signParams();
		
		String orderId = builder.send();
		
		assertEquals(ORDER_ID, orderId);
		
		verify(mexcClientMock).newOrder(parametersCaptor.capture());
		Map<String, Object> parameters = parametersCaptor.getValue();
		assertEquals(signature, parameters.get("signature"));
		
		return new SignatureTestDataHolder(mac, builder);
		
	}
	
	private record SignatureTestDataHolder(Mac mac, NewOrderBuilder builder) {
		
	}

}
