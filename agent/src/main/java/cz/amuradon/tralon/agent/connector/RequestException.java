package cz.amuradon.tralon.agent.connector;

import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class RequestException extends WebApplicationException {

	private static final long serialVersionUID = -6997078136481105337L;
	
	private final ErrorResponse errorResponse;
	
	public RequestException(Response response, ErrorResponse errorResponse) {
		super(response);
		this.errorResponse = errorResponse;
	}

	public ErrorResponse errorResponse() {
		return errorResponse;
	}
}
