package cz.amuradon.tralon.agent.connector;

public interface RestClientListener {

	void onOrderBook(String symbol, String message);
	void onExchangeInfo(String symbol, String message);
}
