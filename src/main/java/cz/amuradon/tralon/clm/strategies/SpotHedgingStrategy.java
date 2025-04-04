package cz.amuradon.tralon.clm.strategies;

import java.math.BigDecimal;
import java.util.Map;

import cz.amuradon.tralon.clm.OrderBook;
import cz.amuradon.tralon.clm.connector.OrderBookUpdate;
import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.WebsocketClient;

public class SpotHedgingStrategy implements Strategy {

	public SpotHedgingStrategy(RestClient restClient, WebsocketClient websocketClient,
			String baseAsset, String quoteAsset, BigDecimal price, BigDecimal baseQuantity) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBaseBalanceUpdate(BigDecimal balance) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onQuoteBalanceUpdate(BigDecimal balance) {
		// TODO Auto-generated method stub

	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return super.toString();
	}

	@Override
	public String getDescription() {
		// TODO complete description
		return getClass().getSimpleName();
	}
}
