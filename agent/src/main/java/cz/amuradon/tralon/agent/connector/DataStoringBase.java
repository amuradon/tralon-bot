package cz.amuradon.tralon.agent.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;

public class DataStoringBase {

	private final ExecutorService executorService;
	
	private final Path dataPath;
	
	public DataStoringBase(final ExecutorService executorService, final Path dataPath) {
		this.executorService = executorService;
		this.dataPath = dataPath;
	}
	
	void writeToFile(byte[] message, String fileName) {
		executorService.execute(() -> {
			try {
				Files.write(dataPath.resolve(fileName),
						message,
						StandardOpenOption.CREATE);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
	}

}
