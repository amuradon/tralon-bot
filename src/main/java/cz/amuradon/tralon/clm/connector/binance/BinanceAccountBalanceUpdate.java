package cz.amuradon.tralon.clm.connector.binance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.clm.connector.AccountBalance;

/**
 * Used for Websocket update message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAccountBalanceUpdate(@JsonProperty("a") String asset,
		@JsonProperty("f") BigDecimal available) implements AccountBalance {

}
