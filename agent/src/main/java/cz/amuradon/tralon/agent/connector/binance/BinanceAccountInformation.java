package cz.amuradon.tralon.agent.connector.binance;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAccountInformation(List<BinanceAccountBalance> balances) {

}
