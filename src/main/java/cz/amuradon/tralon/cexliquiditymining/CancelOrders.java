package cz.amuradon.tralon.cexliquiditymining;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.kucoin.sdk.KucoinRestClient;

import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named(CancelOrders.BEAN_NAME)
@RegisterForReflection
public class CancelOrders {
	
	public static final String BEAN_NAME = "CancelOrders";
	
	private final KucoinRestClient restClient;
    
    private final Map<String, Order> orders;
    
    @Inject
    public CancelOrders(final KucoinRestClient restClient,
    		final Map<String, Order> orders) {
		this.restClient = restClient;
		this.orders = orders;
    }

    public void processOrderChanges(Side side, BigDecimal proposedPrice) {
    	Map<String, Order> ordersBeKept = new ConcurrentHashMap<>();
        for (Entry<String, Order> entry: orders.entrySet()) {
        	Order order = entry.getValue();
        	if (side == order.side() && order.price().compareTo(proposedPrice) != 0) {
        		try {
        			Log.infof("Cancelling order %s", order);
					restClient.orderAPI().cancelOrder(order.orderId());
				} catch (IOException e) {
					throw new IllegalStateException("Could not cancel an order " + order.orderId(), e);
				}
        	} else {
        		ordersBeKept.put(entry.getKey(), order);
        	}
        }
        
        synchronized (orders) {
        	orders.clear();
        	orders.putAll(ordersBeKept);
		}
    }
}
