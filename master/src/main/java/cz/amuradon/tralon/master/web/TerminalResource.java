package cz.amuradon.tralon.master.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/terminal")
@ApplicationScoped
public class TerminalResource {

	public List<Trade> trades;
	
	@Inject
	public TerminalResource() {
		this.trades = new ArrayList<>();
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance terminal(@RestQuery String symbol, @RestQuery String exchange) {
		return Templates.terminal(symbol, exchange, trades);
	}
	
	@POST
	@Path("/buy/{exchange}/{symbol}")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance buy(@RestPath String exchange, @RestPath String symbol) {
		Log.infof("Bought: %s, %s", exchange, symbol);
		trades.add(new Trade(new Date().getTime(), exchange, symbol, "Buy", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
		return Templates.terminal$trades(trades);
	}

	@POST
	@Path("/sellAll/{exchange}/{symbol}")
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance sellAll(@RestPath String exchange, @RestPath String symbol) {
		Log.infof("Sold: %s, %s", exchange, symbol);
		trades.clear();
		return Templates.terminal$trades(trades);
	}
	
	
	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance terminal(String symbol, String exchange, List<Trade> trades);
		public static native TemplateInstance terminal$trades(List<Trade> trades);
	}
	
}
