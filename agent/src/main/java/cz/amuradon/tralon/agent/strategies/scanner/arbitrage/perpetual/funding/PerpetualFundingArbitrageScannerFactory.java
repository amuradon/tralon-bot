package cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.microprofile.reactive.messaging.Channel;

import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import io.quarkus.arc.All;
import io.quarkus.arc.Unremovable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Unremovable  // Required to keep it in container as it is not reference from anywhere.
@ApplicationScoped
public class PerpetualFundingArbitrageScannerFactory {

	private final List<PerpetualFundingRateRestClient> clients;
	private final MutinyEmitter<ChannelData> scannerDataEmmitter;
	private final ScheduledExecutorService scheduler;

	@Inject
	public PerpetualFundingArbitrageScannerFactory(@All List<PerpetualFundingRateRestClient> clients,
			@Channel(ChannelData.CHANNEL_NAME) final MutinyEmitter<ChannelData> scannerDataEmmitter,
			final ScheduledExecutorService scheduler) {
		this.clients = clients;
		this.scannerDataEmmitter = scannerDataEmmitter;
		this.scheduler = scheduler;
	}
	
	public PerpetualFundingArbitrageScanner create() {
		return new PerpetualFundingArbitrageScanner(clients, scannerDataEmmitter, scheduler);
	}
}
