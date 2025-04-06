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
    
}
