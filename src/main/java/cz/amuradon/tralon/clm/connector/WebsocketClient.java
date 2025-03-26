package cz.amuradon.tralon.clm.connector;

import java.util.function.Consumer;

public interface WebsocketClient {

	void onOrderChange(Consumer<OrderChange> callback);
	void onLevel2Data(Consumer<OrderBookChange> callback, String symbol);
	void onAccountBalance(Consumer<AccountBalance> callback);
}
