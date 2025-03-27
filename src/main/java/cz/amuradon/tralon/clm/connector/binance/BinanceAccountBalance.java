package cz.amuradon.tralon.clm.connector.binance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.amuradon.tralon.clm.connector.AccountBalance;

/**
 * Used for REST API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAccountBalance(@JsonProperty("asset") String asset,
		@JsonProperty("free") BigDecimal available) implements AccountBalance {

}
