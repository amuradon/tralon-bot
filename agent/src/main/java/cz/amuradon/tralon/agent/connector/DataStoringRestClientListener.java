package cz.amuradon.tralon.agent.connector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class DataStoringRestClientListener extends DataStoringBase implements RestClientListener {

	public DataStoringRestClientListener(ExecutorService executorService, Path dataPath) {
		super(executorService, dataPath);
	}

	@Override
	public void onOrderBook(String symbol, String message) {
		writeToFile(message.getBytes(StandardCharsets.UTF_8), "depth.json");
	}

	@Override
	public void onExchangeInfo(String symbol, String message) {
		writeToFile(message.getBytes(StandardCharsets.UTF_8), "exchangeInfo.json");
	}

}
