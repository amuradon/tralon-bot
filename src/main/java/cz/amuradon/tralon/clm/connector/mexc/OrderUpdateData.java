package cz.amuradon.tralon.clm.connector.mexc;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderUpdateData(
		@JsonProperty("i") String orderId,
		@JsonProperty("c") String clientOrderId,
//		@JsonProperty("o") int orderType, // TODO enum?
		@JsonProperty("S") Side side,
//		@JsonProperty("p") BigDecimal price,
		@JsonProperty("ap") BigDecimal averagePrice,
		@JsonProperty("v") BigDecimal quantityBase,
//		@JsonProperty("a") BigDecimal amountQuote,
//		@JsonProperty("V") BigDecimal remainingQuantityBase,
//		@JsonProperty("A") BigDecimal remainingAmountQuote,
//		@JsonProperty("d") boolean maker,
		@JsonProperty("cv") BigDecimal cumulativeQuantityBase,
		@JsonProperty("s") Status status
		) {

}
