package cz.amuradon.tralon.agent.connector.mexc;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcTradeEventData(@JsonProperty("deals") List<MexcTrade> deals) {

}
