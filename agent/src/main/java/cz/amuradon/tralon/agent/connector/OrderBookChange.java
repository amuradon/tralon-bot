package cz.amuradon.tralon.agent.connector;

import java.util.List;

public interface OrderBookChange {

	List<OrderBookUpdate> getAsks();

	List<OrderBookUpdate> getBids();

}
