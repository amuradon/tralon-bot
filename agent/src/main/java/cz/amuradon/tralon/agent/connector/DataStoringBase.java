package cz.amuradon.tralon.agent.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DataStoringBase {

	private final String exchangeName;
	
	private final ExecutorService executorService;
	
	private final String dataDir;
	
	// It's not needed for Websocket but no harm to have it here as it is passed to executor service
	private final ObjectMapper mapper;
	
	public DataStoringBase(final String exchangeName,
			final ExecutorService executorService, final String dataDir) {
		this.exchangeName = exchangeName;
		this.executorService = executorService;
		this.dataDir = dataDir;
		mapper = new ObjectMapper();
	}
	
	void writeToFile(String symbol, Object object, String fileName) {
		LocalDate date = LocalDate.now();
		executorService.execute(() -> {
			try {
				Files.writeString(Paths.get(dataDir, exchangeName,
						date.format(DateTimeFormatter.ofPattern("yyyyMMdd")), symbol, fileName),
						mapper.writeValueAsString(object),
						StandardOpenOption.CREATE);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
	}
	
	void writeToFile(String symbol, String message, String fileName) {
		LocalDate date = LocalDate.now();
		executorService.execute(() -> {
			try {
				Files.writeString(Paths.get(dataDir, exchangeName,
						date.format(DateTimeFormatter.ofPattern("yyyyMMdd")), symbol, fileName),
						message,
						StandardOpenOption.CREATE);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
	}

}
