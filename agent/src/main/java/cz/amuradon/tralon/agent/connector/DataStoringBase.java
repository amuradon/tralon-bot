package cz.amuradon.tralon.agent.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class DataStoringBase {

	private final ExecutorService executorService;
	
	private final Path dataPath;
	
	private Function<byte[], byte[]> transformer;
	
	public DataStoringBase(final ExecutorService executorService, final Path dataPath) {
		this.executorService = executorService;
		this.dataPath = dataPath;
		this.transformer = Function.identity();
	}
	
	void writeToFile(byte[] message, String fileName) {
		executorService.execute(() -> {
			try {
				Files.write(dataPath.resolve(fileName),
						transformer.apply(message),
						StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write to file", e);
			}
		});
	}

	public void setTransformer(Function<byte[], byte[]> transformer) {
		this.transformer = transformer;
	}
}
