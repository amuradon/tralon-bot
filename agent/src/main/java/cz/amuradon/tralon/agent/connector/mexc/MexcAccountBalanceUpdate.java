package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.agent.connector.AccountBalance;

/**
 * Used for Websocket update message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcAccountBalanceUpdate(@JsonProperty("a") String asset,
		@JsonProperty("f") BigDecimal available) implements AccountBalance {

}
