package cz.amuradon.tralon.clm;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cz.amuradon.tralon.clm.model.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class BeanConfig {

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
    
}
