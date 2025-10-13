package cz.amuradon.tralon.agent.connector.binancealpha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenList(BinanceAlphaTicker[] data) {

}
