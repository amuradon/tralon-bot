package cz.amuradon.tralon.agent.connector.kucoin.futures;

import java.util.List;

public record KucoinActiveContractsResponse(List<KucoinContract> data) {

}
