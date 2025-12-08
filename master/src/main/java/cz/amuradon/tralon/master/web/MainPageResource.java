package cz.amuradon.tralon.master.web;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.function.TriFunction;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.RestForm;

import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.strategies.Strategy;
import cz.amuradon.tralon.agent.strategies.StrategyFactory;
import cz.amuradon.tralon.agent.strategies.marketmaking.MarketMakingStrategy;
import cz.amuradon.tralon.agent.strategies.marketmaking.SpreadStrategies;
import cz.amuradon.tralon.agent.strategies.newlisting.ComputeInitialPrice;
import cz.amuradon.tralon.agent.strategies.newlisting.FixedPercentClosePositionUpdatesProcessor;
import cz.amuradon.tralon.agent.strategies.newlisting.NewListingStrategy;
import cz.amuradon.tralon.agent.strategies.newlisting.TrailingProfitStopUpdatesProcessor;
import cz.amuradon.tralon.agent.strategies.newlisting.UpdatesProcessor;
import cz.amuradon.tralon.agent.strategies.scanner.MomentumScannerStrategy;
import cz.amuradon.tralon.agent.strategies.scanner.ScannerData;
import cz.amuradon.tralon.agent.strategies.scanner.SymbolAlert;
import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.Shutdown;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@ApplicationScoped
public class MainPageResource {

	private static final String MARKET_MAKING = "Market Making";
	private static final String MOMENTUM_SCANNER = "Momentum Scanner";
	private static final String NEW_LISTING = "New Listing";

	private final StrategyFactory strategyFactory;
	
	private final Map<String, Strategy> runningStrategies;
	
	private final List<String> supportedExchanges;
	
	private final ScheduledExecutorService scheduler;
	
	private final MutinyEmitter<SymbolAlert> symbolAlertsEmmitter;
	
	private final MutinyEmitter<ScannerData> scannerDataEmmitter;
	
	int counter;
	
	// XXX As of now agent as dep, in future managed by Kubernetes #24
	@Inject
	public MainPageResource(final StrategyFactory strategyFactory,
			final ScheduledExecutorService scheduler,
			@Channel(SymbolAlert.CHANNEL) final MutinyEmitter<SymbolAlert> symbolAlertsEmmitter,
			@Channel(ScannerData.CHANNEL) final MutinyEmitter<ScannerData> scannerDataEmmitter) {
		this.strategyFactory = strategyFactory;
		runningStrategies = new ConcurrentSkipListMap<>();
		supportedExchanges = Arrays.stream(Exchange.values()).map(Exchange::displayName).collect(Collectors.toList());
		this.scheduler = scheduler;
		this.symbolAlertsEmmitter = symbolAlertsEmmitter;
		this.scannerDataEmmitter = scannerDataEmmitter;
	}
	
	@Shutdown
	public void onShutdown() {
		runningStrategies.values().forEach(Strategy::stop);
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance index() {
		return Templates.index(Arrays.asList(MOMENTUM_SCANNER, MARKET_MAKING, NEW_LISTING));
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
	public TemplateInstance chooseStrategy(@RestForm String strategy) {
		Log.infof("Chosen strategy: %s", strategy);
		if (strategy.equalsIgnoreCase(MOMENTUM_SCANNER)) {
			return Templates.momentumScanner(supportedExchanges);
		} else if (strategy.equalsIgnoreCase(MARKET_MAKING)) {
			return Templates.marketMaking(supportedExchanges, Arrays.stream(SpreadStrategies.values())
					.collect(Collectors.toMap(e -> e.displayName(), e -> e.valueCaption())));
		} else if (strategy.equalsIgnoreCase(NEW_LISTING)) {
			return Templates.newListing(supportedExchanges);
		} else {
			return Templates.noneStrategy();
		}
	}

	@POST
	@Path("/run-momentum-scanner")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance runMomentumScanner(@RestForm String exchangeName, @RestForm int refreshInterval,
			@RestForm int usdVolume24h,	@RestForm BigDecimal priceChange, @RestForm BigDecimal volumeChange) {
		final Exchange exchange = Exchange.fromDisplayName(exchangeName);
		return runStrategy(exchangeName, "0", "0", false, (r, w, s) ->
				new MomentumScannerStrategy(exchange, r, scheduler, refreshInterval, usdVolume24h, priceChange,
					volumeChange, symbolAlertsEmmitter, scannerDataEmmitter));
	}

	@POST
	@Path("/run-market-making")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance runMarketMaking(@RestForm String exchangeName,
			@RestForm String baseAsset, @RestForm String quoteAsset,
			@RestForm BigDecimal quoteQuantity,
			@RestForm int priceChangeDelayMs,
			@RestForm String spreadStrategy,
			@RestForm BigDecimal spreadStrategyValue) {
		return runStrategy(exchangeName, baseAsset, quoteAsset, false, (r, w, s) -> new MarketMakingStrategy(r, w, baseAsset,
				quoteAsset, s, priceChangeDelayMs, quoteQuantity, scheduler,
				SpreadStrategies.fromDisplayName(spreadStrategy).create(spreadStrategyValue)));
	}

	// TODO extrahovat scheduling mimo strategii
	@POST
	@Path("/run-new-listing")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance runNewListing(@RestForm String exchangeName,
			@RestForm String baseAsset,
			@RestForm String quoteAsset,
			@RestForm BigDecimal quoteQuantity,
			@RestForm String priceExpr,
			@RestForm LocalDateTime listingDateTime,
			@RestForm int buyOrderRequestsPerSecond,
			@RestForm int buyOrderMaxAttempts,
			@RestForm String takeProfitStopLoss,
			@RestForm int initialBuyOrderValidityMs,
			@RestForm boolean storeData) {
		boolean checked = logAndCheck("buyOrderRequestsPerSecond", buyOrderRequestsPerSecond);
		checked &= logAndCheck("buyOrderMaxAttempts", buyOrderMaxAttempts);
		checked &= logAndCheck("initialBuyOrderValidityMs", initialBuyOrderValidityMs);
		
		Properties properties = new Properties();
		try {
			properties.load(new StringReader(takeProfitStopLoss));
		} catch (IOException e) {
			throw new IllegalStateException("Could not parse TP / SL properties", e);
		}
		String tpSlType = properties.getProperty("type");
		int takeProfit = Integer.parseInt(properties.getProperty("takeProfit"));
		int stopLoss = Integer.parseInt(properties.getProperty("stopLoss"));
		int stopLossDelayMs = Integer.parseInt(properties.getProperty("stopLossDelayMs"));
		
		BiFunction<RestClient, String, UpdatesProcessor> updatesProcessorFactory = switch (tpSlType) {
		case "fixed": {
			yield (r, s) -> new FixedPercentClosePositionUpdatesProcessor(r, s, takeProfit, stopLoss, stopLossDelayMs, initialBuyOrderValidityMs);
		}
		case "trailing": {
			yield (r, s) -> new TrailingProfitStopUpdatesProcessor(r, s, stopLoss, stopLossDelayMs, initialBuyOrderValidityMs);
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + tpSlType
					+ ". Allowed values are 'fixed' or 'trailing'");
		};
		
		
		Log.infof("storeData: %s", storeData);
		if (checked) {
			return runStrategy(exchangeName, baseAsset, quoteAsset, storeData,
					(r, w, s) -> new NewListingStrategy(scheduler, r, w,
					new ComputeInitialPrice(priceExpr), quoteQuantity, s, listingDateTime, buyOrderRequestsPerSecond,
					buyOrderMaxAttempts, updatesProcessorFactory.apply(r, s)));
		} else {
			return Templates.runningStrategies(runningStrategies);
		}
	}
	
	private boolean logAndCheck(String field, int value) {
		if (value <= 0) {
			Log.errorf("%s has invalid value: %d" , field, value);
			return false;
		} else {
			Log.infof("%s: %d", field, value);
			return true;
		}
	}

	/* TODO
	 * Error pri pokusu spustit bezici strategii se na webu nezobrazi, jen v logu diky Log
	 * Input type="number" acceptuje pouze cela cisla
	 * Pri stisku Run tlacitka by se mel formular vymazat
	 * MM strategie: prepinani Spread strategy nemeni SS value
	 */
	private TemplateInstance runStrategy(String exchangeName, String baseAsset, String quoteAsset,
			boolean storeData,
			TriFunction<RestClient, WebsocketClient, String, Strategy> strategyCreation) {
		final String id = strategyId(exchangeName, baseAsset, quoteAsset);
		if (runningStrategies.containsKey(id)) {
			String error = String.format("There is already running strategy for %s/%s on %s",
					baseAsset, quoteAsset, exchangeName);
			Log.error(error);
			throw new BadRequestException(error);
		}
		Strategy strategy = strategyFactory.create(exchangeName, baseAsset, quoteAsset, storeData, strategyCreation);
		strategy.start();
		String strategyDescription = strategy.getDescription();
		runningStrategies.put(id, strategy);
		Log.infof("Started strategy: %s - %s", id, strategyDescription);
		return Templates.runningStrategies(runningStrategies);
	}
	
	private String strategyId(String exchange, String baseAsset, String quoteAsset) {
		return exchange + " - " + baseAsset + "/" + quoteAsset;
	}

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance index(List<String> strategies);
		public static native TemplateInstance runningStrategies(Map<String, Strategy> runningStrategies);
		public static native TemplateInstance momentumScanner(List<String> exchanges);
		public static native TemplateInstance marketMaking(List<String> exchanges, Map<String, String> spreadStrategies);
		public static native TemplateInstance newListing(List<String> exchanges);
		public static native TemplateInstance noneStrategy();
	}
	
}
