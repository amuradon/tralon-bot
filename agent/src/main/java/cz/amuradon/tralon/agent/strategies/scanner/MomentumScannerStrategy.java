package cz.amuradon.tralon.agent.strategies.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.Ticker;
import cz.amuradon.tralon.agent.strategies.Strategy;
import cz.amuradon.tralon.agent.strategies.SymbolValues;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.MutinyEmitter;

public class MomentumScannerStrategy implements Strategy {

	private final Exchange exchange;
	private final RestClient restClient;
	private final ScheduledExecutorService scheduler;
	private final int priceDelta;
	private final Map<String, LinkedList<SymbolValues>> movingValues;
	private final MutinyEmitter<SymbolAlert> symbolAlertsEmmitter;
	private final MutinyEmitter<ScannerData> scannerDataEmitter;
	private final Set<String> reported;
	private ScheduledFuture<?> task;
	
	public MomentumScannerStrategy(Exchange exchange, RestClient restClient, final ScheduledExecutorService scheduler,
			int priceDelta, MutinyEmitter<SymbolAlert> symbolAlertsEmmitter, MutinyEmitter<ScannerData> scannerDataEmitter) {
		this.exchange = exchange;
		this.restClient = restClient;
		this.scheduler = scheduler;
		this.priceDelta = priceDelta;
		this.movingValues = new HashMap<>();
		this.symbolAlertsEmmitter = symbolAlertsEmmitter;
		this.scannerDataEmitter = scannerDataEmitter;
		reported = new HashSet<>();
	}

	@Override
	public void start() {
		Ticker[] tickers = restClient.ticker();
		for (Ticker ticker : tickers) {
			if (filter(ticker)) {
				LinkedList<SymbolValues> values = initiateValues(ticker);
				movingValues.put(ticker.symbol(), values);
			}
		}
		task = scheduler.scheduleAtFixedRate(this::scan, 1, 1, TimeUnit.MINUTES);
	}
	
	private LinkedList<SymbolValues> initiateValues(Ticker ticker) {
		LinkedList<SymbolValues> values = new LinkedList<>();
		for (int i = 0; i < 15; i++) {
			values.add(new SymbolValues(ticker.lastPrice(), ticker.quoteVolume()));
		}
		return values;
	}
	
	void scan() {
		Ticker[] tickers = restClient.ticker();
		Set<String> reportingThisCycle = new HashSet<>();
		List<ScannerDataItem> items = new ArrayList<>();
		for (Ticker ticker : tickers) {
			if (filter(ticker)) {
				String symbol = ticker.symbol();
				LinkedList<SymbolValues> values = movingValues.computeIfAbsent(symbol, k -> initiateValues(ticker));
				SymbolValues m15 = values.getFirst();
				SymbolValues m5 = values.get(9);
				SymbolValues m1 = values.getLast();
				
				// Spocitat % rozdilu pro cenu a volume
				BigDecimal lastPrice = ticker.lastPrice();
				BigDecimal quoteVolume = ticker.quoteVolume();
				
				BigDecimal m1PriceChange = getPercentage(lastPrice, m1.lastPrice());
				BigDecimal m5PriceChange = getPercentage(lastPrice, m5.lastPrice());
				BigDecimal m15PriceChange = getPercentage(lastPrice, m15.lastPrice());
				if (aboveThreshold(m1PriceChange) || aboveThreshold(m5PriceChange)) {
					BigDecimal m1VolumeChange = getPercentage(quoteVolume, m1.quoteVolume());
					BigDecimal m5VolumeChange = getPercentage(quoteVolume, m5.quoteVolume());
					BigDecimal m15VolumeChange = getPercentage(quoteVolume, m15.quoteVolume());
					reportingThisCycle.add(symbol);
					items.add(new ScannerDataItem(symbol, exchange.displayName(),
							String.format("1p: %s, 5p: %s, 15p: %s, 1v: %s, 5v: %s, 15v: %s",
							m1PriceChange, m5PriceChange, m15PriceChange, m1VolumeChange, m5VolumeChange,
							m15VolumeChange)));
					
					if (!reported.contains(symbol)) {
						Log.infof("Notifying %s %s", exchange.displayName(), symbol);
						if (symbolAlertsEmmitter.hasRequests()) {
							symbolAlertsEmmitter.sendAndForget(new SymbolAlert(symbol, exchange.displayName(),
									String.format("%s Symbol %s, 1p: %s, 5p: %s, 15p: %s, 1v: %s, 5v: %s, 15v: %s", exchange.displayName(),
									symbol,
									m1PriceChange, m5PriceChange, m15PriceChange, m1VolumeChange, m5VolumeChange,
									m15VolumeChange)));
						}
					}
				}
				values.removeFirst();
				values.addLast(new SymbolValues(ticker.lastPrice(), ticker.quoteVolume()));
			}
		}
		if (scannerDataEmitter.hasRequests()) {
			scannerDataEmitter.sendAndForget(new ScannerData(exchange.displayName(), items));
		}
		reported.clear();
		reported.addAll(reportingThisCycle);
	}

	private BigDecimal getPercentage(BigDecimal current, BigDecimal previous) {
		return current.subtract(previous).divide(safeZero(previous), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
	}
	
	private BigDecimal safeZero(BigDecimal value) {
		return value.compareTo(BigDecimal.ZERO) == 0 ? new BigDecimal("0.000001") : value;
	}
	
	private boolean aboveThreshold(BigDecimal value) {
		return value.compareTo(BigDecimal.valueOf(priceDelta)) == 1;
	}
	
	private boolean filter(Ticker ticker) {
		return exchange.momentumTokenfilter(ticker) && ticker.quoteVolume().compareTo(BigDecimal.valueOf(1000000)) == 1;
	}

	@Override
	public void stop() {
		task.cancel(true);
	}

	@Override
	public String getDescription() {
		return "Momentum scanner";
	}

	@Override
	public String link() {
		return "/scanner";
	}

}
