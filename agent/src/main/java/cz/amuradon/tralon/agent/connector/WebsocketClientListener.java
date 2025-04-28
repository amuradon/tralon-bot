package cz.amuradon.tralon.agent.connector;

public interface WebsocketClientListener {

	void onOrderBookUpdate(String symbol, String message);
	void onTrade(String symbol, String message);
	void onAccountBalanceUpdate(String message);
	void onOrderUpdate(String message);
}
