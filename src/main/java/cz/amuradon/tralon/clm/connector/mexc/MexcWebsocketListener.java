package cz.amuradon.tralon.clm.connector.mexc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

public interface MexcWebsocketListener {

	void onMessage(String message);

	@Default
	@ApplicationScoped
	public static class NoopListener implements MexcWebsocketListener {

		@Override
		public void onMessage(String message) {
			// No-op
		}
		
	}
}
