package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.clm.connector.OrderBookUpdate;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.WebsocketClient;

public class SpotHedgingStrategy implements Strategy {
	
	private final RestClient restClient;
	private final WebsocketClient websocketClient;
	private final String baseAsset;
	private final String quoteAsset;
	private final BigDecimal price;
	private final BigDecimal baseQuantity;

	public SpotHedgingStrategy(RestClient restClient, WebsocketClient websocketClient,
			String baseAsset, String quoteAsset, BigDecimal price, BigDecimal baseQuantity) {
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.baseAsset = baseAsset;
		this.quoteAsset = quoteAsset;
		this.price = price;
		this.baseQuantity = baseQuantity;
	}

	@Override
	public void start() {
		// TODO
		// subscribe trade (?) updates
		//   * monitor price to place buy or sell order
		// track order state - websocket or REST API?
		// validate balance
		// validate symbol
		// validate price (might be already above)
	}
	
	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		// Do nothing, not needed

	}

	@Override
	public void onBaseBalanceUpdate(BigDecimal balance) {
		// TODO Need to track?

	}

	@Override
	public void onQuoteBalanceUpdate(BigDecimal balance) {
		// TODO Need to track?

	}

	@Override
	public void stop() {
		// TODO
		// close all opened orders
		// sell asset?
	}
 
	@Override
	public String getDescription() {
		// TODO complete description
		return getClass().getSimpleName();
	}
}
