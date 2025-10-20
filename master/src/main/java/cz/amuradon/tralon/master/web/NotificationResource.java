package cz.amuradon.tralon.master.web;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.RestStreamElementType;

import cz.amuradon.tralon.agent.Notification;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@Path("/notifications")
@ApplicationScoped
public class NotificationResource {

	@Inject
	@Channel("notifications")
	Multi<Notification> notifications;
	
	@GET
	@RestStreamElementType(MediaType.APPLICATION_JSON)
	public Multi<Notification> stream() {
		return notifications;
	}
}
