package cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.messaging.Channel;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import cz.amuradon.tralon.agent.strategies.Strategy;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.MutinyEmitter;

public class PerpetualFundingArbitrageScanner implements Strategy {

	public static final String DASHBOARD_LINK = "/perpFundArbScanner";
	
	private final List<PerpetualFundingRateRestClient> clients;
	private final MutinyEmitter<ChannelData> scannerDataEmmitter;
	private final ScheduledExecutorService scheduler;

	private ScheduledFuture<?> task;
	
	public PerpetualFundingArbitrageScanner(@All List<PerpetualFundingRateRestClient> clients,
			@Channel(ChannelData.CHANNEL_NAME) final MutinyEmitter<ChannelData> scannerDataEmmitter,
			final ScheduledExecutorService scheduler) {
		this.clients = clients;
		this.scannerDataEmmitter = scannerDataEmmitter;
		this.scheduler = scheduler;
	}
	
	public void scan() {
		try {
			Log.info("Scanning...");
			
			LocalDateTime closestFundingHour = LocalDateTime.now(ZoneId.of("UTC")).plusHours(1).truncatedTo(ChronoUnit.HOURS);
			long nextFundingDateTime = closestFundingHour.toInstant(ZoneOffset.of("+0")).toEpochMilli();
			Log.debugf("Next funding time: %d (%s)", nextFundingDateTime, closestFundingHour);
			
			HashMap<String, FundingRatesRow> table = new HashMap<>();
			for (PerpetualFundingRateRestClient client : clients) {
				for (FundingRate rate : client.fundingRates()) {
					FundingRatesRow row = table.computeIfAbsent(rate.symbol(), k -> new FundingRatesRow(k));
					row.add(client.exchangeName(), rate, nextFundingDateTime);
				}
			}
			
			List<FundingRatesRow> sorted = table.values().stream().filter(r -> r.fundingDateTime == nextFundingDateTime)
					.sorted((o1, o2) -> o2.diff.compareTo(o1.diff)).collect(Collectors.toList());
			// TODO filter diff > 0.5%
			
			List<String> sortedExchangeNames = clients.stream().map(c -> c.exchangeName()).sorted()
					.collect(Collectors.toList());
			
			Log.debugf("Sending to the channel: %s", sorted);
			if (scannerDataEmmitter.hasRequests()) {
				scannerDataEmmitter.sendAndForget(new ChannelData(sorted, sortedExchangeNames, nextFundingDateTime));
			}
		} catch (Exception e) {
			Log.error("Something went wrong:", e);
		}
	}
	
	@Override
	public void start() {
		task = scheduler.scheduleAtFixedRate(this::scan, 0, 1, TimeUnit.MINUTES);
	}

	@Override
	public void stop() {
		task.cancel(true);
	}

	@Override
	public String getDescription() {
		return "Perp Funding Arbitrage Scanner";
	}

	@Override
	public String link() {
		return DASHBOARD_LINK;
	}
	

	public static PerpetualFundingArbitrageScanner create() {
		PerpetualFundingArbitrageScannerFactory strategy =
				Arc.container().instance(PerpetualFundingArbitrageScannerFactory.class).get();
		return strategy.create();
	}
	
	public class FundingRatesRow {
		
		private final String symbol;
		private final Map<String, FundingRate> fundingRates;
		private long fundingDateTime = Long.MAX_VALUE;
		private BigDecimal low = ZERO; // Minimum is at least 0% as it can be hedged on spot market
		private BigDecimal high = new BigDecimal(-10);
		private BigDecimal diff;
		 
		private FundingRatesRow(String symbol) {
			this.symbol = symbol;
			fundingRates = new HashMap<>();
		}

		public void add(String exchangeName, FundingRate rate, long nextFundingDateTime) {
			fundingRates.put(exchangeName, rate);
			if (rate.nextFundingTime() != nextFundingDateTime) {
				if (low.compareTo(ZERO) <= 0 && high.compareTo(ZERO) < 0) {
					high = ZERO;
				}
			} else {
				low = low.min(rate.lastFundingRate());
				high = high.max(rate.lastFundingRate());
			}
			diff = high.subtract(low).abs();
			fundingDateTime = Math.min(rate.nextFundingTime(), fundingDateTime);
		}
	
		@Override
		public String toString() {
			return String.format("%s: %s (%s, %s) %s %s\n", symbol, diff, low, high, fundingDateTime, fundingRates);
		}

		public BigDecimal diff() {
			return diff;
		}

		public String symbol() {
			return symbol;
		}

		public Map<String, FundingRate> fundingRates() {
			return fundingRates;
		}
	}
}
