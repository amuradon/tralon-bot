package cz.amuradon.tralon.master.web;

import org.jboss.resteasy.reactive.RestQuery;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/terminal")
@ApplicationScoped
public class TerminalResource {

	
	@Inject
	public TerminalResource() {
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance terminal(@RestQuery String symbol, @RestQuery String exchange) {
		return Templates.terminal(symbol, exchange);
	}

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance terminal(String symbol, String exchange);
	}
	
}
