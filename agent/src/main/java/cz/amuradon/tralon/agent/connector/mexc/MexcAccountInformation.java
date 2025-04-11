package cz.amuradon.tralon.agent.connector.mexc;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcAccountInformation(List<MexcAccountBalance> balances) {

}
