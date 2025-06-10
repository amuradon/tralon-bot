package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.Trade;

public interface UpdatesProcessor {

	void processTradeUpdate(Trade trade);
	
	void processOrderUpdate(OrderChange orderChange);

	void querySymbolInfo();

	void setClientOrderId(String clientOrderId);

	void setBuyOrderId(String buyOrderId);

	void setInitialBuyPrice(BigDecimal initialBuyPrice);
}
