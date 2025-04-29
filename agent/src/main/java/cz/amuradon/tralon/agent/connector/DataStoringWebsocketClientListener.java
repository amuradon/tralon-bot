package cz.amuradon.tralon.agent.connector;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class DataStoringWebsocketClientListener extends DataStoringBase implements WebsocketClientListener {

	public DataStoringWebsocketClientListener(final ExecutorService executorService, final Path dataDir) {
		super(executorService, dataDir);
	}
	
	@Override
	public void onOrderBookUpdate(String symbol, String message) {
		writeToFile(symbol, message, "orderBookUpdates.json");

	}

	@Override
	public void onTrade(String symbol, String message) {
		writeToFile(symbol, message, "trades.json");
	}

	@Override
	public void onAccountBalanceUpdate(String message) {
		// TODO do budoucna
	}

	@Override
	public void onOrderUpdate(String message) {
		// TODO do budoucna
	}

}
