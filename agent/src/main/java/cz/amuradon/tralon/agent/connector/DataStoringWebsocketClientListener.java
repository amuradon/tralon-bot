package cz.amuradon.tralon.agent.connector;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class DataStoringWebsocketClientListener extends DataStoringBase implements WebsocketClientListener {

	private final String fileExtension;
	
	public DataStoringWebsocketClientListener(final ExecutorService executorService, final Path dataDir,
			final String fileExtension) {
		super(executorService, dataDir);
		this.fileExtension = fileExtension;
	}
	
	@Override
	public void onOrderBookUpdate(String symbol, byte[] message) {
		writeToFile(message, "orderBookUpdates." + fileExtension);

	}

	@Override
	public void onTrade(String symbol, byte[] message) {
		writeToFile(message, "trades." + fileExtension);
	}

	@Override
	public void onAccountBalanceUpdate(byte[] message) {
		// TODO do budoucna
	}

	@Override
	public void onOrderUpdate(byte[] message) {
		// TODO do budoucna
	}

}
