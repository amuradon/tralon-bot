package cz.amuradon.tralon.agent.connector;

import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;

public class TradeDirectionNotAllowed extends NewOrderError {

	public TradeDirectionNotAllowed(WebApplicationException exception, ErrorResponse errorResponse) {
		super(exception, errorResponse);
	}

}
