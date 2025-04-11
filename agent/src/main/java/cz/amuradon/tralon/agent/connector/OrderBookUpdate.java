package cz.amuradon.tralon.agent.connector;

import java.math.BigDecimal;

import cz.amuradon.tralon.agent.OrderBook;
import cz.amuradon.tralon.agent.Side;

/**
 * The order book price level. Used both for parsing of REST API response as well as Websocket message.
 */
public interface OrderBookUpdate {

	long sequence();
	BigDecimal price();
	BigDecimal size();
	Side side();
	boolean setSequenceIfShouldBeProcessed(OrderBook orderBook);
}
