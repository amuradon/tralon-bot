package cz.amuradon.tralon.clm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;

import org.jboss.resteasy.reactive.RestForm;

import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/spot-hedging-strategy")
public class SpotHedgingStrategyResource {

	private final Map<UUID, String> runningStrategies;
	
	public SpotHedgingStrategyResource() {
		runningStrategies = new ConcurrentSkipListMap<>();
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance index() {
		return Templates.index(Arrays.asList("MEXC", "Kucoin", "Binance"));
	}

	@POST
	@Path("/run")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance run(@RestForm String exchangeName, @RestForm LocalDateTime endDateTime, @RestForm String baseAsset,
			@RestForm String quoteAsset, @RestForm BigDecimal price, @RestForm BigDecimal baseQuantity) {
		// TODO run strategy
		String result = "Success - exchange: " + exchangeName
				+ ", end: " + endDateTime + ", base asset: " + baseAsset + ", quote asset: " + quoteAsset
				+ ", price: " + price + ", base qty: " + baseQuantity;
		Log.info(result);
		runningStrategies.put(UUID.randomUUID(), result);
		return Templates.runningStrategies(runningStrategies);
	}

	@GET
	@Path("/get")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance get() {
		return Templates.runningStrategies(runningStrategies);
	}

	@POST
	@Path("/stop")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance stop(@RestForm String stopUuid) {
		Log.infof("Stopping %s", stopUuid);
		
		String strategy = runningStrategies.remove(UUID.fromString(stopUuid));
		
		// TODO stop strategy
		
		return Templates.runningStrategies(runningStrategies);
	}

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance index(List<String> exchanges);
		public static native TemplateInstance runningStrategies(Map<UUID, String> runningStrategies);
	}
}
