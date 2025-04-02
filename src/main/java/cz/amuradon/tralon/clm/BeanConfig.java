package cz.amuradon.tralon.clm;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cz.amuradon.tralon.clm.model.Order;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeanConfig {

	public static final String SYMBOL = "symbol";
	
	private final EngineFactory strategyFactory;
	
	@Inject
	public BeanConfig(final EngineFactory strategyFactory) {
		this.strategyFactory = strategyFactory;
	}
	
    @ApplicationScoped
    @Produces
    public Map<String, Order> orders() {
    	return new ConcurrentHashMap<>();
    }
    
    @ApplicationScoped
    @Produces
    public Map<Side, PriceProposal> proposals() {
    	ConcurrentHashMap<Side, PriceProposal> proposals = new ConcurrentHashMap<>();
		proposals.put(Side.BUY, new PriceProposal());
		proposals.put(Side.SELL, new PriceProposal());
		return proposals;
    }
    
    // XXX Pravdepodobne ne tak ciste reseni, asi bych mel delegovat do vlastniho vlakna?
//    @Startup
//    public void start() {
//    	new Thread(strategyFactory.create(), "Startup").start();
//    }
}
