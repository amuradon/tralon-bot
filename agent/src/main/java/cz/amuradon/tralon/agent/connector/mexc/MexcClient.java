package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import cz.amuradon.tralon.agent.connector.Kline;
import cz.amuradon.tralon.agent.connector.ListenKey;
import cz.amuradon.tralon.agent.connector.NoValidTradePriceException;
import cz.amuradon.tralon.agent.connector.RequestException;
import cz.amuradon.tralon.agent.connector.TradeDirectionNotAllowedException;
import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "mexc-api")
@Retry(maxRetries = 10)
public interface MexcClient {

	// Used when trading in new listing not started yet
	String TRANS_DIRECTION_NOT_ALLOWED = "30001";
			
	String NO_VALID_TRADE_PRICE = "30010";
	
	@Path("/exchangeInfo")
	@GET
	String exchangeInfo(@RestQuery String symbol);
	
	@Path("/ticker/24hr")
	@GET
	MexcTicker[] ticker();
	
	@Path("/klines")
	@GET
	Kline[] klines(@RestQuery String symbol, @RestQuery String interval, @RestQuery int limit);

	@Path("/depth")
	@GET
	@ClientQueryParam(name = "limit", value = "5000")
	String orderBook(@RestQuery String symbol);
	
	@Path("/account")
	@GET
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	List<MexcAccountBalance> listBalances(@RestQuery Map<String, Object> queryParams);
	
	@Path("/openOrders")
	@GET
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	List<MexcOrder> openOrders(@RestQuery Map<String, Object> queryParams);
	
	@Path("/order")
	@POST
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	@Retry(maxRetries = 0) // This retry needs to handled programmatically
	OrderResponse newOrder(@RestQuery Map<String, Object> queryParams);
	
	@Path("/order")
	@DELETE
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	OrderResponse cancelOrder(@RestQuery Map<String, Object> queryParams);

	@Path("/userDataStream")
	@POST
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	ListenKey userDataStream(@RestQuery Map<String, Object> queryParams);
	
	@ClientExceptionMapper
	static RuntimeException toException(Response response) {
		ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
		int status = response.getStatus();
		Log.errorf("ERR response: %d - %s: %s, Headers: %s", status,
				response.getStatusInfo().getReasonPhrase(), errorResponse, response.getHeaders());
		if (NO_VALID_TRADE_PRICE.equalsIgnoreCase(errorResponse.code())) {
			Matcher matcher = Pattern.compile(".*\\s(\\d+(\\.\\d+)?)USDT").matcher(errorResponse.msg());
			if (matcher.find()) {
				String maxPrice = matcher.group(1);
				return new NoValidTradePriceException(response, new BigDecimal(maxPrice), errorResponse);
			}
		} else if (TRANS_DIRECTION_NOT_ALLOWED.equalsIgnoreCase(errorResponse.code())) {
			return new TradeDirectionNotAllowedException(response, errorResponse);
		}
		return new RequestException(response, errorResponse);
	}
}
