package cz.amuradon.tralon.agent.connector;

import java.util.function.Consumer;

public interface WebsocketClient {

	void onOrderChange(Consumer<OrderChange> callback);
	void onOrderBookChange(Consumer<OrderBookChange> callback, String symbol);
	void onAccountBalance(Consumer<AccountBalance> callback);
}
