package cz.amuradon.tralon.agent.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DataStoringBase {

	private final ExecutorService executorService;
	
	private final Path dataPath;
	
	// It's not needed for Websocket but no harm to have it here as it is passed to executor service
	private final ObjectMapper mapper;
	
	public DataStoringBase(final ExecutorService executorService, final Path dataPath) {
		this.executorService = executorService;
		this.dataPath = dataPath;
		mapper = new ObjectMapper();
	}
	
	void writeToFile(String symbol, Object object, String fileName) {
		executorService.execute(() -> {
			try {
				Files.writeString(dataPath.resolve(fileName),
						mapper.writeValueAsString(object),
						StandardOpenOption.CREATE);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
	}
	
	void writeToFile(String symbol, String message, String fileName) {
		executorService.execute(() -> {
			try {
				Files.writeString(dataPath.resolve(fileName),
						message,
						StandardOpenOption.CREATE);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
	}

}
