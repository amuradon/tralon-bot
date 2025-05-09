package cz.amuradon.tralon.agent.connector;

public interface WebsocketClientListener {

	void onOrderBookUpdate(String symbol, byte[] message);
	void onTrade(String symbol, byte[] message);
	void onAccountBalanceUpdate(byte[] message);
	void onOrderUpdate(byte[] message);
}
