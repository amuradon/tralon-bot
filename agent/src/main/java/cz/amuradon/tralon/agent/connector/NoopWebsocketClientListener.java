package cz.amuradon.tralon.agent.connector;

import java.util.function.Function;

public class NoopWebsocketClientListener implements WebsocketClientListener {

	@Override
	public void onOrderBookUpdate(String symbol, byte[] message) {
		// No-op
	}

	@Override
	public void onTrade(String symbol, byte[] message) {
		// No-op
	}

	@Override
	public void onAccountBalanceUpdate(byte[] message) {
		// No-op
	}

	@Override
	public void onOrderUpdate(byte[] message) {
		// No-op
	}

	@Override
	public void setTransformer(Function<byte[], byte[]> transformer) {
		// No-op
	}

}
