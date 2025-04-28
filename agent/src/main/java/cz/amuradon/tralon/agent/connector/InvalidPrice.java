package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;

public class InvalidPrice extends NewOrderError {

	private final BigDecimal validPrice;
	
	public InvalidPrice(WebApplicationException exception, BigDecimal validPrice, ErrorResponse errorResponse) {
		super(exception, errorResponse);
		this.validPrice = validPrice;
	}

	public BigDecimal validPrice() {
		return validPrice;
	}

}
