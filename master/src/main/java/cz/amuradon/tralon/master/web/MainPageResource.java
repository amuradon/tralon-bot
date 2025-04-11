package cz.amuradon.tralon.master.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.lang3.function.TriFunction;
import org.jboss.resteasy.reactive.RestForm;

import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.RestClientFactory;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.connector.WebsocketClientFactory;
import cz.amuradon.tralon.agent.strategies.DualInvestmentSpotHedgeStrategy;
import cz.amuradon.tralon.agent.strategies.Strategy;
import cz.amuradon.tralon.agent.strategies.marketmaking.MarketMakingStrategy;
import cz.amuradon.tralon.agent.strategies.marketmaking.SpreadStrategies;
import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class MainPageResource {

	private static final String MARKET_MAKING = "Market Making";
	private static final String SPOT_HEDGE = "Dual Investment Spot Hedge";

	private final Map<String, Strategy> runningStrategies;
	
	private final List<String> supportedExchanges;
	
	private final Instance<RestClient> restClientFactory;
	
	private final Instance<WebsocketClient> websocketClientFactory;
	
	private final ScheduledExecutorService scheduler;
	
	// XXX As of now agent as dep, in future managed by Kubernetes #24
	@Inject
	public MainPageResource(@RestClientFactory Instance<RestClient> restClientFactory,
			@WebsocketClientFactory Instance<WebsocketClient> websocketClientFactory,
			final ScheduledExecutorService scheduler) {
		runningStrategies = new ConcurrentSkipListMap<>();
		supportedExchanges = Arrays.stream(Exchange.values()).map(Exchange::displayName).collect(Collectors.toList());
		this.restClientFactory = restClientFactory;
		this.websocketClientFactory = websocketClientFactory;
		this.scheduler = scheduler;
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance index() {
		return Templates.index(Arrays.asList(SPOT_HEDGE, MARKET_MAKING));
	}

	@GET
	@Path("/get-running")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance getRunning() {
		return Templates.runningStrategies(runningStrategies);
	}

	@POST
	@Path("/stop-strategy")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance stop(@RestForm String stopId) {
		Log.infof("Stopping strategy: %s", stopId);
		
		Strategy strategy = runningStrategies.remove(stopId);
		
		strategy.stop();
		
		Log.infof("Stopped strategy: %s", stopId);
		return Templates.runningStrategies(runningStrategies);
	}
	
	@POST
	@Path("/choose-strategy")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance runSpotHedge(@RestForm String strategy) {
		Log.info(strategy);
		if (strategy.equalsIgnoreCase(SPOT_HEDGE)) {
			return Templates.dualInvestmentSpotHedge(supportedExchanges);
		} else if (strategy.equalsIgnoreCase(MARKET_MAKING)) {
			return Templates.marketMaking(supportedExchanges, Arrays.stream(SpreadStrategies.values())
					.collect(Collectors.toMap(e -> e.displayName(), e -> e.valueCaption())));
		} else {
			return Templates.noneStrategy();
		}
	}

	@POST
	@Path("/run-spot-hedge")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance runSpotHedge(@RestForm String exchangeName, @RestForm LocalDateTime endDateTime,
			@RestForm String baseAsset, @RestForm String quoteAsset, @RestForm BigDecimal price,
			@RestForm BigDecimal baseQuantity, @RestForm BigDecimal mmSpread,
			@RestForm BigDecimal apr, @RestForm int priceChangeDelayMs) {
		return runStrategy(exchangeName, baseAsset, quoteAsset, (r, w, s) ->
				new DualInvestmentSpotHedgeStrategy(r, w, baseAsset, quoteAsset, s,
						price, baseQuantity, mmSpread, apr, priceChangeDelayMs));
	}

	@POST
	@Path("/run-market-making")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance runMarketMaking(@RestForm String exchangeName,
			@RestForm String baseAsset, @RestForm String quoteAsset,
			@RestForm BigDecimal baseQuantity,
			@RestForm int priceChangeDelayMs,
			@RestForm String spreadStrategy,
			@RestForm BigDecimal spreadStrategyValue) {
		return runStrategy(exchangeName, baseAsset, quoteAsset, (r, w, s) -> new MarketMakingStrategy(r, w, baseAsset,
				quoteAsset, s, priceChangeDelayMs, baseQuantity, scheduler,
				SpreadStrategies.fromDisplayName(spreadStrategy).create(spreadStrategyValue)));
	}
	
	/* TODO
	 * Error pri pokusu spustit bezici strategii se na webu nezobrazi, jen v logu diky Log
	 * Input type="number" acceptuje pouze cela cisla
	 * Pri stisku Run tlacitka by se mel formular vymazat
	 * MM strategie: prepinani Spread strategy nemeni SS value
	 */
	private TemplateInstance runStrategy(String exchangeName, String baseAsset, String quoteAsset,
			TriFunction<RestClient, WebsocketClient, String, Strategy> strategyFactory) {
		final String id = strategyId(exchangeName, baseAsset, quoteAsset);
		if (runningStrategies.containsKey(id)) {
			String error = String.format("There is already running strategy for %s/%s on %s",
					baseAsset, quoteAsset, exchangeName);
			Log.error(error);
			throw new BadRequestException(error);
		}
		final Exchange exchange = Exchange.fromDisplayName(exchangeName);
		final RestClient restClient =
				restClientFactory.select(RestClientFactory.LITERAL, exchange.qualifier()).get();
		final WebsocketClient websocketClient =
				websocketClientFactory.select(WebsocketClientFactory.LITERAL, exchange.qualifier()).get();
		Strategy strategy = strategyFactory.apply(restClient, websocketClient, exchange.symbol(baseAsset, quoteAsset));
		strategy.start();
		String strategyDescription = strategy.getDescription();
		runningStrategies.put(id, strategy);
		Log.infof("Started strategy: %s - %s", id, strategyDescription);
		return Templates.runningStrategies(runningStrategies);
	}
	
	private String strategyId(String exchange, String baseAsset, String quoteAsset) {
		return String.join(":", exchange, baseAsset, quoteAsset);
	}

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance index(List<String> strategies);
		public static native TemplateInstance runningStrategies(Map<String, Strategy> runningStrategies);
		public static native TemplateInstance dualInvestmentSpotHedge(List<String> exchanges);
		public static native TemplateInstance marketMaking(List<String> exchanges, Map<String, String> spreadStrategies);
		public static native TemplateInstance noneStrategy();
	}
	
}
