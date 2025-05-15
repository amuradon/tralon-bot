package cz.amuradon.tralon.agent.connector;

import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.core.Response;

public class TradeDirectionNotAllowedException extends RequestException {

	private static final long serialVersionUID = 8623161751101033965L;

	public TradeDirectionNotAllowedException(Response response, ErrorResponse errorResponse) {
		super(response, errorResponse);
	}

}
