package cz.amuradon.tralon.agent.connector;

import cz.amuradon.tralon.agent.strategies.newlisting.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;

public class NewOrderError {

	private final WebApplicationException exception;
	
	private final ErrorResponse errorResponse;
	
	public NewOrderError(final WebApplicationException exception, ErrorResponse errorResponse) {
		this.exception = exception;
		this.errorResponse = errorResponse;
	}

	public WebApplicationException exception() {
		return exception;
	}

	public ErrorResponse errorResponse() {
		return errorResponse;
	}
}
