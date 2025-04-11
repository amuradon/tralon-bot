package cz.amuradon.tralon.agent.connector;

import java.util.LinkedHashMap;

public class RequestUtils {

	public static ParamsBuilder param(String key, Object value) {
		return new ParamsBuilder().param(key, value);
	}

	public static final class ParamsBuilder extends LinkedHashMap<String, Object> {
		
		private static final long serialVersionUID = 1L;

		public ParamsBuilder param(String key, Object value) {
			put(key, value);
			return this;
		}

	}
}
