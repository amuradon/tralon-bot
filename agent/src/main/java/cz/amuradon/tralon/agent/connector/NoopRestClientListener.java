package cz.amuradon.tralon.agent.connector;

public class NoopRestClientListener implements RestClientListener {

	@Override
	public void onOrderBook(String symbol, String message) {
		// No-op
	}

	@Override
	public void onExchangeInfo(String symbol, String message) {
		// No-op
	}

}
