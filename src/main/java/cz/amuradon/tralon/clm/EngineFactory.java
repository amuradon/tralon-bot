package cz.amuradon.tralon.clm;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.clm.connector.RestClient;
import cz.amuradon.tralon.clm.connector.WebsocketClient;
import cz.amuradon.tralon.clm.model.Order;
import cz.amuradon.tralon.clm.strategies.Strategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;


@ApplicationScoped
public class EngineFactory {

	private final RestClient restClient;
    
	private final WebsocketClient websocketClient;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final String symbol;
	
	private final Map<String, Order> orders;
	
	private final OrderBookManager orderBookManager;
	
	private final Strategy strategy;
	
	@Inject
    public EngineFactory(final RestClient restClient,
    		final WebsocketClient websocketClient,
    		@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken,
    		@Named(BeanConfig.SYMBOL) final String symbol,
    		final Map<String, Order> orders,
    		final OrderBookManager orderBookManager,
    		final Strategy strategy) {
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		this.symbol = symbol;
		this.orders = orders;
		this.orderBookManager = orderBookManager;
		this.strategy = strategy;
    }
	
	public Runnable create() {
		return new Engine(restClient, websocketClient, baseToken, quoteToken, symbol,
				orders, orderBookManager, strategy);
	}
}
