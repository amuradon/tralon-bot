package cz.amuradon.tralon.agent.connector;

import java.util.function.Consumer;

public interface WebsocketClient {

	void onOrderChange(Consumer<OrderChange> callback);
	void onAccountBalance(Consumer<AccountBalance> callback);
	void onOrderBookChange(Consumer<OrderBookChange> callback, String symbol);
	void onTrade(Consumer<Trade> callback, String symbol);
	void setListener(WebsocketClientListener listener);
	void close();
}
