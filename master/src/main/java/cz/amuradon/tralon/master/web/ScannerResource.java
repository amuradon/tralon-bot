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

import cz.amuradon.tralon.agent.Notification;
import cz.amuradon.tralon.agent.connector.Exchange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.strategies.MomentumScannerStrategy;
import cz.amuradon.tralon.agent.strategies.Strategy;
import cz.amuradon.tralon.agent.strategies.StrategyFactory;
import cz.amuradon.tralon.agent.strategies.marketmaking.MarketMakingStrategy;
import cz.amuradon.tralon.agent.strategies.marketmaking.SpreadStrategies;
import cz.amuradon.tralon.agent.strategies.newlisting.ComputeInitialPrice;
import cz.amuradon.tralon.agent.strategies.newlisting.FixedPercentClosePositionUpdatesProcessor;
import cz.amuradon.tralon.agent.strategies.newlisting.NewListingStrategy;
import cz.amuradon.tralon.agent.strategies.newlisting.TrailingProfitStopUpdatesProcessor;
import cz.amuradon.tralon.agent.strategies.newlisting.UpdatesProcessor;
import cz.amuradon.tralon.master.web.MainPageResource.Templates;
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

@Path("/scanner")
@ApplicationScoped
public class ScannerResource {

	@Inject
	public ScannerResource() {
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
		return Templates.scanner();
	}

	@GET
	@Path("/table")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance getRunning() {
		return Templates.table();
	}
	
	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance scanner();
		public static native TemplateInstance table();
	}
	
}
