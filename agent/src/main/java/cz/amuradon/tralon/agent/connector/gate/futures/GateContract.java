package cz.amuradon.tralon.agent.connector.gate.futures;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GateContract(
		@JsonProperty("name") String symbol,
		@JsonProperty("funding_rate") BigDecimal fundingFeeRate,
		@JsonProperty("funding_next_apply") long nextFundingRateDateTime) {

}
