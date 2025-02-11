package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;

import org.apache.camel.Body;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named(OrderBookManager.BEAN_NAME)
@RegisterForReflection
public class OrderBookManager {

	public static final String BEAN_NAME = "orderBookManager";
	
	private OrderBook orderBook;
	
	public void processOrderBook(@Body OrderBook orderBook) {
		this.orderBook = orderBook;
	}
	
	public void processUpdate(OrderBookUpdate update) {
		final long sequence = update.sequence();
		if (sequence > orderBook.sequence()) {
			if (update.side() == Side.BUY) {
				if (update.size().compareTo(BigDecimal.ZERO) == 0) {
					orderBook.getBids().remove(update.price());
				} else {
					orderBook.getBids().put(update.price(), update.size());
				}
			} else if (update.side() == Side.SELL) {
				if (update.size().compareTo(BigDecimal.ZERO) == 0) {
					orderBook.getAsks().remove(update.price());
				} else {
					orderBook.getAsks().put(update.price(), update.size());
				}
			}
			orderBook.sequence(sequence);
			System.out.println("OB update: " + update);
			System.out.println(orderBook);
		}
	}
}
