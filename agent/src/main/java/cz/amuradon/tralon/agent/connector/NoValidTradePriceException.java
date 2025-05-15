package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.core.Response;

public class NoValidTradePriceException extends RequestException {

	private static final long serialVersionUID = 8136075807360181607L;

	private final BigDecimal validPrice;
	
	public NoValidTradePriceException(Response response, BigDecimal validPrice, ErrorResponse errorResponse) {
		super(response, errorResponse);
		this.validPrice = validPrice;
	}

	public BigDecimal validPrice() {
		return validPrice;
	}

}
