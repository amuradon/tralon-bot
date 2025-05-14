package cz.amuradon.tralon.agent.strategies;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.function.TriFunction;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.agent.connector.DataStoringRestClientListener;
import cz.amuradon.tralon.agent.connector.DataStoringWebsocketClientListener;
import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class StrategyFactory {

	private final Instance<RestClient> restClientFactory;
	
	private final Instance<WebsocketClient> websocketClientFactory;
	
	private final ExecutorService executorService;
	
	private final String dataRootPath;
	
	@Inject
	public StrategyFactory(@RestClientFactory Instance<RestClient> restClientFactory,
			@WebsocketClientFactory Instance<WebsocketClient> websocketClientFactory,
			final ExecutorService executorService,
			@ConfigProperty(name = "data.path") final String dataRootPath) {
		this.restClientFactory = restClientFactory;
		this.websocketClientFactory = websocketClientFactory;
		this.executorService = executorService;
		this.dataRootPath = dataRootPath;
	}
	
	public Strategy create(String exchangeName, String baseAsset, String quoteAsset,
			boolean storeData,
			TriFunction<RestClient, WebsocketClient, String, Strategy> strategyCreation) {
		final Exchange exchange = Exchange.fromDisplayName(exchangeName);
		RestClient restClient =
				restClientFactory.select(RestClientFactory.LITERAL, exchange.qualifier()).get();
		final WebsocketClient websocketClient =
				websocketClientFactory.select(WebsocketClientFactory.LITERAL, exchange.qualifier()).get();
		
		String symbol = exchange.symbol(baseAsset, quoteAsset);
		if (storeData) {
			String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
			java.nio.file.Path dataPath = java.nio.file.Path.of(dataRootPath, exchangeName, dateFolder, symbol);
			try {
				Files.createDirectories(dataPath);
			} catch (IOException e) {
				throw new IllegalStateException("Could not create data folders.", e);
			}
			restClient.setListener(new DataStoringRestClientListener(executorService, dataPath));
			websocketClient.setListener(new DataStoringWebsocketClientListener(executorService, dataPath,
					exchange.getWebsocketDataFileExtesion()));
		}

		return strategyCreation.apply(restClient, websocketClient, symbol);
	}
	
	
}
