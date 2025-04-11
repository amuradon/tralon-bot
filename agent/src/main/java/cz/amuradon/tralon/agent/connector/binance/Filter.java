package cz.amuradon.tralon.agent.connector.binance;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class Filter {

	public String filterType;
	private Map<String, String> properties = new HashMap<>();
	
	@JsonAnySetter
    public void add(String key, String value) {
        properties.put(key, value);
    }
	
	public String get(String key) {
		return properties.get(key);
	}
}
