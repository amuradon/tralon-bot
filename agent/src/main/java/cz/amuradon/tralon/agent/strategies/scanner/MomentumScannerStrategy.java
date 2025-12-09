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
import java.util.stream.Collectors;

import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.Kline;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.Ticker;
import cz.amuradon.tralon.agent.strategies.Strategy;
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
	private final int tickersInMinute;
	private final Map<String, LinkedList<Ticker>> movingValues;
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
		this.refreshInterval = refreshInterval * 10;
		this.usdVolume24h = usdVolume24h;
		this.priceChange = priceChange;
		this.volumeChange = volumeChange;
		this.tickersInMinute = 60 / this.refreshInterval;
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
				LinkedList<Ticker> values = initiateValues(ticker);
				movingValues.put(ticker.symbol(), values);
			}
		}
		task = scheduler.scheduleAtFixedRate(this::scan, 0, refreshInterval, TimeUnit.SECONDS);
	}
	
	private LinkedList<Ticker> initiateValues(Ticker ticker) {
		LinkedList<Ticker> values = new LinkedList<>();
		for (int i = 0; i < 5 * tickersInMinute; i++) {
			values.add(ticker);
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
				LinkedList<Ticker> values = movingValues.computeIfAbsent(symbol, k -> initiateValues(ticker));
				Ticker m_5 = values.getFirst();
				Ticker m_2 = values.get(values.size() - 2 * tickersInMinute);
				Ticker m_1 = values.get(values.size() - tickersInMinute);
				
				// XXX Kline nevraci aktualni candle, tzn. volume sleduji po 1m, i kdyz je frekvence market dat pull vetsi!
				// FIXME nejak divne se pocita volume, ale z Kline REST API se cte spravne
				
				BigDecimal lastPrice = ticker.lastPrice();
				
				BigDecimal m1PriceChange = getPercentage(lastPrice, m_1.lastPrice());
				BigDecimal m2PriceChange = getPercentage(lastPrice, m_2.lastPrice());
				BigDecimal m5PriceChange = getPercentage(lastPrice, m_5.lastPrice());
				if (priceFilter(m1PriceChange) || priceFilter(m2PriceChange) || priceFilter(m5PriceChange)) {
					Log.debugf("Price filter PASSED %s: %s -> p[$]: %s, %s, %s, %s, d[%%]: %s, %s, %s\n%s",
							exchange.displayName(),	symbol,
							m_5, m_2, m_1, lastPrice,
							m5PriceChange, m2PriceChange, m1PriceChange, 
							values.stream().map(Ticker::toString).collect(Collectors.joining("\n")));
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
						String alert = String.format("24h: %s, w: %s, 1p: %s, 2p: %s, 5p: %s, vol: %s, %s, %s",
							ticker.priceChangePercent(), getPercentage(ticker.lastPrice(), ticker.weightedAvgPrice()),
							m1PriceChange, m2PriceChange, m5PriceChange,
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
					} 
					Log.debugf("Volume filter %s %s: %s -> v[T]: %s (avg), %s, %s, %s, d[%%]: %s, %s, %s",
							volumeFilterResult, exchange.displayName(),	symbol,
							volumeData.average(), volumeData.lastVolume_2(), volumeData.lastVolume_1(), volumeData.lastVolume(),
							volumeDiff_2, volumeDiff_1, volumeDiff);
				}
				values.removeFirst();
				values.addLast(ticker);
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
