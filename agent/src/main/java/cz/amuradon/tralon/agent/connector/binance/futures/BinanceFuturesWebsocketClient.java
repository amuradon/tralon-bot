package cz.amuradon.tralon.agent.connector.binance.futures;

import java.util.function.Consumer;

import cz.amuradon.tralon.agent.connector.AccountBalance;
import cz.amuradon.tralon.agent.connector.OrderBookChange;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.Trade;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import cz.amuradon.tralon.agent.connector.WebsocketClientListener;
import jakarta.enterprise.context.Dependent;

@Dependent
@BinanceFutures
@WebsocketClientFactory // Required for proper usage with Instance
public class BinanceFuturesWebsocketClient implements WebsocketClient {

	// Not used for now but needs to exist due to injections
	@Override
	public void onOrderChange(Consumer<OrderChange> callback) {
	}

	@Override
	public void onOrderBookChange(Consumer<OrderBookChange> callback, String symbol) {
	}

	@Override
	public void onAccountBalance(Consumer<AccountBalance> callback) {
	}

	@Override
	public void onTrade(Consumer<Trade> callback, String symbol) {
		
	}

	@Override
	public void setListener(WebsocketClientListener listener) {
		
	}

	@Override
	public void close() {
		
	}

}
