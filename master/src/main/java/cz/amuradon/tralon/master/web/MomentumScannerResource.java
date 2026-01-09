package cz.amuradon.tralon.master.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.RestStreamElementType;

import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.strategies.scanner.momentum.MomentumScannerStrategy;
import cz.amuradon.tralon.agent.strategies.scanner.momentum.ScannerData;
import cz.amuradon.tralon.agent.strategies.scanner.momentum.ScannerDataItem;
import cz.amuradon.tralon.agent.strategies.scanner.momentum.SymbolAlert;
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

@Path(MomentumScannerStrategy.DASHBOARD_LINK)
@ApplicationScoped
public class MomentumScannerResource {

	private final Multi<ScannerData> scannerDataChannel;
	private final Multi<SymbolAlert> symbolAlertsChannel;
	private final Sse sse;
	private Map<String, List<ScannerDataItem>> data;
	
	@Inject
	public MomentumScannerResource(@Channel(ScannerData.CHANNEL) Multi<ScannerData> scannerDataChannel,
			@Channel(SymbolAlert.CHANNEL) Multi<SymbolAlert> symbolAlertsChannel,
			Sse sse) {
		this.scannerDataChannel = scannerDataChannel;
		this.symbolAlertsChannel = symbolAlertsChannel;
		this.sse = sse;
		data = new LinkedHashMap<>();
	}
	
	/*
	 * FIXME
	 * - z nejakeho duvodu se objevuji notifikace dvakrat
	 */
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance scanner() {
		return Templates.momentumScanner(Arrays.stream(Exchange.values()).map(Exchange::displayName).collect(Collectors.toList()), data);
	}

	// FIXME vice scanneru se pere o tabulku, prepisuji si ji
	@GET
	@Path("/tableUpdates")
	@RestStreamElementType(MediaType.TEXT_HTML)
	public Multi<OutboundSseEvent> tableUpdates() {
		return scannerDataChannel.map(d -> {
			data.put(d.exchange(), d.data());
			return sse.newEvent(d.exchange(), Templates.momentumScanner$exchangeTable(d.data()).render());
		});
	}

	@GET
	@Path("/symbolAlerts")
	@RestStreamElementType(MediaType.TEXT_PLAIN)
	public Multi<OutboundSseEvent> symbolAlerts() {
		return symbolAlertsChannel.map(d -> sse.newEventBuilder().name(d.exchange()).data(d).build());
	}
	
	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance momentumScanner(List<String> exchanges, Map<String, List<ScannerDataItem>> data);
		public static native TemplateInstance momentumScanner$exchangeTable (List<ScannerDataItem> list);
	}
	
}
