package cz.amuradon.tralon.clm.connector.binance;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeInfo(List<SymbolInfo> symbols) {

}
