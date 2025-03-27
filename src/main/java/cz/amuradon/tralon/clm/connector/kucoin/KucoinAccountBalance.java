package cz.amuradon.tralon.clm.connector.kucoin;

import java.math.BigDecimal;

import com.kucoin.sdk.rest.response.AccountBalancesResponse;
import com.kucoin.sdk.websocket.event.AccountChangeEvent;

import cz.amuradon.tralon.clm.connector.AccountBalance;

public class KucoinAccountBalance implements AccountBalance {

	private final String token;
	private final BigDecimal available;
	
	public KucoinAccountBalance(final AccountChangeEvent data) {
		this.token = data.getCurrency();
		this.available = data.getAvailable();
	}

	public KucoinAccountBalance(AccountBalancesResponse data) {
		this.token = data.getCurrency();
		this.available = data.getAvailable();
	}

	@Override
	public String asset() {
		return token;
	}

	@Override
	public BigDecimal available() {
		return available;
	}

}
