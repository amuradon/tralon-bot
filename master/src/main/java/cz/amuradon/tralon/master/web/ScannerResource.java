package cz.amuradon.tralon.master.web;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.RestStreamElementType;

import cz.amuradon.tralon.agent.strategies.scanner.ScannerData;
import cz.amuradon.tralon.agent.strategies.scanner.ScannerDataItem;
import cz.amuradon.tralon.agent.strategies.scanner.SymbolAlert;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/scanner")
@ApplicationScoped
public class ScannerResource {

	private final Multi<ScannerData> scannerDataChannel;
	private final Multi<SymbolAlert> symbolAlertsChannel;
	private List<ScannerDataItem> items;
	
	@Inject
	public ScannerResource(@Channel(ScannerData.CHANNEL) Multi<ScannerData> scannerDataChannel,
			@Channel(SymbolAlert.CHANNEL) Multi<SymbolAlert> symbolAlertsChannel) {
		this.scannerDataChannel = scannerDataChannel;
		this.symbolAlertsChannel = symbolAlertsChannel;
		items = new ArrayList<>();
	}
	
	/*
	 * TODO
	 * - musim rozdelit SSE pro notifikace a tabulku, protoze tabulku chci aktualizovat kazdou minutu,
	 * ale notifikace posilat jen pri novem objevenem tokenu
	 * - vracet tabulku jako HTML? Pres Qute nejak? K tomu asi pouziji hx-get a hx-trigger na child elementu,
	 *   viz https://github.com/bigskysoftware/htmx-extensions/blob/main/test/ws-sse/static/sse-triggers.html
	 * FIXME
	 * - z nejakeho duvodu se objevuji notifikace dvakrat
	 */
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance scanner() {
		return Templates.scanner(items);
	}

	@GET
	@Path("/table")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance getRunning() {
		return Templates.scanner$scannerTable(items);
	}
	
	// FIXME vice scanneru se pere o tabulku, prepisuji si ji
	@GET
	@Path("/tableUpdates")
	@RestStreamElementType(MediaType.TEXT_HTML)
	public Multi<String> tableUpdates() {
		return scannerDataChannel.map(d -> {
			items = d.data();
			return Templates.scanner$scannerTable(d.data()).render();
		});
	}

	// FIXME Asi to neposila jako JSON 
	// MessageEvent {isTrusted: true, data: 'SymbolAlert[title=New token, body=Some token]', origin: 'http://localhost:9091', lastEventId: '', source: null, …}	
	@GET
	@Path("/symbolAlerts")
	@RestStreamElementType(MediaType.APPLICATION_JSON)
	public Multi<SymbolAlert> symbolAlerts() {
		return symbolAlertsChannel;
	}
	
	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance scanner(List<ScannerDataItem> items);
		public static native TemplateInstance scanner$scannerTable(List<ScannerDataItem> items);
	}
	
}
