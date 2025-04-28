package cz.amuradon.tralon.agent.connector;

public class NoopWebsocketClientListener implements WebsocketClientListener {

	@Override
	public void onOrderBookUpdate(String symbol, String message) {
		// No-op
	}

	@Override
	public void onTrade(String symbol, String message) {
		// No-op
	}

	@Override
	public void onAccountBalanceUpdate(String message) {
		// No-op
	}

	@Override
	public void onOrderUpdate(String message) {
		// No-op
	}

}
