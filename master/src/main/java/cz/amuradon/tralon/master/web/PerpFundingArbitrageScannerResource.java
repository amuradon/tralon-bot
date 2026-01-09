package cz.amuradon.tralon.master.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.RestStreamElementType;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding.ChannelData;
import cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding.PerpetualFundingArbitrageScanner;
import cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding.PerpetualFundingArbitrageScanner.FundingRatesRow;
import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

@Path(PerpetualFundingArbitrageScanner.DASHBOARD_LINK)
@ApplicationScoped
public class PerpFundingArbitrageScannerResource {
	
	private final Multi<ChannelData> scannerDataChannel;
	private final Sse sse;
	private List<List<String>> data;
	
	@Inject
	public PerpFundingArbitrageScannerResource(
			@Channel(ChannelData.CHANNEL_NAME) Multi<ChannelData> scannerDataChannel,
			Sse sse) {
		this.scannerDataChannel = scannerDataChannel;
		this.sse = sse;
		data = new ArrayList<>();
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance perpFundingArbScanner() {
		return Templates.perpFundingArbScanner(data);
	}

	@GET
	@Path("/tableUpdates")
	@RestStreamElementType(MediaType.TEXT_HTML)
	public Multi<OutboundSseEvent> tableUpdates() {
		return scannerDataChannel.map(d -> {
			
			List<List<String>> data = new ArrayList<>();
			
			// Header
			List<String> header = new ArrayList<>();
			header.add("Symbol");
			header.add("Best Rate");
			header.addAll(d.sortedExchangeNames());
			data.add(header);
			
			for (FundingRatesRow ratesRow : d.sortedRows()) {
				List<String> row = new ArrayList<>();
				row.add(ratesRow.symbol());
				row.add(ratesRow.diff().multiply(new BigDecimal(100)).toString());
				for (String exchangeName : d.sortedExchangeNames()) {
					FundingRate rate = ratesRow.fundingRates().get(exchangeName);
					if (rate == null) {
						row.add("---");
					} else if (rate.nextFundingTime() != d.closestFundingMillis()) {
						row.add("0.0 *");
					} else {
						row.add(rate.lastFundingRate().multiply(new BigDecimal(100)).toPlainString());
					}
				}
				data.add(row);
			}
			
			this.data = data;
			return sse.newEvent("data", Templates.perpFundingArbScanner$scannerTable(data).render());
		});
	}

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance perpFundingArbScanner(List<List<String>> data);
		public static native TemplateInstance perpFundingArbScanner$scannerTable(List<List<String>> data);
	}
	
}
