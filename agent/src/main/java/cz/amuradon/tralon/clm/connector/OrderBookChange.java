package cz.amuradon.tralon.clm.connector;

import java.util.List;

public interface OrderBookChange {

	List<OrderBookUpdate> getAsks();

	List<OrderBookUpdate> getBids();

}
