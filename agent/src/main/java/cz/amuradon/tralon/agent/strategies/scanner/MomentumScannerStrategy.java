package cz.amuradon.tralon.agent.strategies.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.Kline;
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
	private final int refreshInterval;
	private final int usdVolume24h;
	private final BigDecimal priceChange;
	private final BigDecimal volumeChange;
	private final Map<String, LinkedList<SymbolValues>> movingValues;
	private final MutinyEmitter<SymbolAlert> symbolAlertsEmmitter;
	private final MutinyEmitter<ScannerData> scannerDataEmitter;
	private final Map<String, String> reported;
	private ScheduledFuture<?> task;
	
	public MomentumScannerStrategy(Exchange exchange, RestClient restClient, final ScheduledExecutorService scheduler,
			int refreshInterval, int usdVolume24h, BigDecimal priceChange, BigDecimal volumeChange,
			MutinyEmitter<SymbolAlert> symbolAlertsEmmitter, MutinyEmitter<ScannerData> scannerDataEmitter) {
		this.exchange = exchange;
		this.restClient = restClient;
		this.scheduler = scheduler;
		this.refreshInterval = refreshInterval;
		this.usdVolume24h = usdVolume24h;
		this.priceChange = priceChange;
		this.volumeChange = volumeChange;
		this.movingValues = new HashMap<>();
		this.symbolAlertsEmmitter = symbolAlertsEmmitter;
		this.scannerDataEmitter = scannerDataEmitter;
		reported = new HashMap<>();
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
		task = scheduler.scheduleAtFixedRate(this::scan, 0, refreshInterval, TimeUnit.SECONDS);
	}
	
	private LinkedList<SymbolValues> initiateValues(Ticker ticker) {
		LinkedList<SymbolValues> values = new LinkedList<>();
		for (int i = 0; i < 15; i++) {
			values.add(new SymbolValues(ticker.lastPrice(), ticker.quoteVolume()));
		}
		return values;
	}
	
	void scan() {
		Log.infof("Scanning %s...", exchange.displayName());
		Ticker[] tickers = restClient.ticker();
		Map<String, String> reportingThisCycle = new HashMap<>();
		List<ScannerDataItem> items = new ArrayList<>();
		String currentCycleTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		for (Ticker ticker : tickers) {
			if (filter(ticker)) {
				String symbol = ticker.symbol();
				// FIXME moving values je hard-coded pro refresh 1m, ne pro nastavitelny
				LinkedList<SymbolValues> values = movingValues.computeIfAbsent(symbol, k -> initiateValues(ticker));
				SymbolValues m15 = values.getFirst();
				SymbolValues m5 = values.get(9);
				SymbolValues m1 = values.getLast();
				
				BigDecimal lastPrice = ticker.lastPrice();
				
				BigDecimal m1PriceChange = getPercentage(lastPrice, m1.lastPrice());
				BigDecimal m5PriceChange = getPercentage(lastPrice, m5.lastPrice());
				BigDecimal m15PriceChange = getPercentage(lastPrice, m15.lastPrice());
				if (priceFilter(m1PriceChange) || priceFilter(m5PriceChange)) {
					Log.debugf("Price filter PASSED %s: %s, %s, %s, %s", exchange.displayName(),
							symbol, m1PriceChange, m5PriceChange, m15PriceChange);
					VolumeData volumeData = calculatedVolumeDiffs(symbol);
					BigDecimal volumeDiff_2 = getPercentage(volumeData.lastVolume_2(), volumeData.average());
					BigDecimal volumeDiff_1 = getPercentage(volumeData.lastVolume_1(), volumeData.average());
					BigDecimal volumeDiff = getPercentage(volumeData.lastVolume(), volumeData.average());
					String volumeFilterResult = "FAILED";
					if (volumeFilter(volumeDiff) || volumeFilter(volumeDiff_1) || volumeFilter(volumeDiff_2)) {
						volumeFilterResult = "PASSED";
					
						String timestamp = currentCycleTimestamp;
						boolean isNew = true;
						String previouslyReported = reported.get(symbol);
						if (previouslyReported != null) {
							timestamp = previouslyReported;
							isNew = false;
						}
						reportingThisCycle.put(symbol, timestamp);
						String alert = String.format("24h: %s, w: %s, 1p: %s, 5p: %s, 15p: %s, vol: %s, %s, %s",
							ticker.priceChangePercent(), getPercentage(ticker.lastPrice(), ticker.weightedAvgPrice()),
							m1PriceChange, m5PriceChange, m15PriceChange,
							volumeDiff_2,
							volumeDiff_1,
							volumeDiff);
						Log.infof("%s Symbol %s, %s", exchange.displayName(), symbol, alert);
						items.add(new ScannerDataItem(symbol, exchange.displayName(),
								exchange.exchangeLink(ticker), timestamp, isNew,
								alert, exchange.tradingViewLink(ticker)));
						
						if (!reported.containsKey(symbol)) {
							Log.infof("Notifying %s %s", exchange.displayName(), symbol);
							if (symbolAlertsEmmitter.hasRequests()) {
								symbolAlertsEmmitter.sendAndForget(new SymbolAlert(symbol, exchange.displayName(),
										timestamp));
							}
						}
					} else {
					}
					Log.debugf("Volume filter %s %s: %s, %s, %s, %s", volumeFilterResult, exchange.displayName(),
							symbol, volumeDiff_2, volumeDiff_1, volumeDiff);
				}
				values.removeFirst();
				values.addLast(new SymbolValues(ticker.lastPrice(), ticker.quoteVolume()));
			}
		}
		if (scannerDataEmitter.hasRequests()) {
			scannerDataEmitter.sendAndForget(new ScannerData(exchange.displayName(), items));
		}
		reported.clear();
		reported.putAll(reportingThisCycle);
	}
	
	private VolumeData calculatedVolumeDiffs(String symbol) {
		Kline[] klines = restClient.klines(symbol, "1m", 100);
		BigDecimal volumeSum = BigDecimal.ZERO;
		
		for(int i = 0; i < klines.length - 3; i++) {
			volumeSum = volumeSum.add(klines[i].volume());
		}
		BigDecimal averageVolume = volumeSum.divide(new BigDecimal(klines.length));

		return new VolumeData(averageVolume, klines[klines.length - 3].volume(),
				klines[klines.length - 2].volume(), klines[klines.length - 1].volume());
	}
	
	private BigDecimal getPercentage(BigDecimal current, BigDecimal previous) {
		return current.subtract(previous).divide(safeZero(previous), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
	}
	
	private BigDecimal safeZero(BigDecimal value) {
		return value.compareTo(BigDecimal.ZERO) == 0 ? new BigDecimal("0.000001") : value;
	}
	
	private boolean priceFilter(BigDecimal value) {
		return value.compareTo(priceChange) == 1;
	}

	private boolean volumeFilter(BigDecimal value) {
		return value.compareTo(volumeChange.multiply(new BigDecimal(100))) == 1;
	}
	
	private boolean filter(Ticker ticker) {
		return exchange.momentumTokenfilter(ticker) 
				&& ticker.quoteVolume().compareTo(BigDecimal.valueOf(usdVolume24h)) == 1;
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
