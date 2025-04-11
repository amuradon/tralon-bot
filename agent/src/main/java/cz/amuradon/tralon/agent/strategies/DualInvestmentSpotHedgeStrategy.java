package cz.amuradon.tralon.agent.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.agent.connector.OrderBookUpdate;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.WebsocketClient;

public class DualInvestmentSpotHedgeStrategy implements Strategy {
	
	private final RestClient restClient;
	private final WebsocketClient websocketClient;
	private final String baseAsset;
	private final String quoteAsset;
	private final String symbol;
	private final BigDecimal price;
	private final BigDecimal baseQuantity;
	private final BigDecimal marketMakingSpread;
	private final BigDecimal apr;
	private final int priceChangeDelayMs;

	public DualInvestmentSpotHedgeStrategy(RestClient restClient, WebsocketClient websocketClient,
			String baseAsset, String quoteAsset, String symbol, BigDecimal price, BigDecimal baseQuantity,
			BigDecimal marketMakingSpread, BigDecimal apr, int priceChangeDelayMs) {
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.baseAsset = baseAsset;
		this.quoteAsset = quoteAsset;
		this.symbol = symbol;
		this.price = price;
		this.baseQuantity = baseQuantity;
		this.marketMakingSpread = marketMakingSpread;
		this.apr = apr;
		this.priceChangeDelayMs = priceChangeDelayMs;
	}

	@Override
	public void start() {
		// TODO
		// nacti order book -> mid-price
		// pokud je cena v rozmezi +- APR za den od smluvene ceny - pouzij market making
		// pokud je cena nad smluvena cena +APR za den -> HODL
		// pokud je cena pod smluvenou cenou +APR za den -> prodej
		// delay
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
