package cz.amuradon.tralon.clm.connector.mexc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponse(@JsonProperty("orderId") String orderId) {

}
