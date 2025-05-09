package cz.amuradon.tralon.agent.connector.mexc;

import java.math.BigDecimal;

import com.mxc.push.common.protobuf.PrivateAccountV3Api;

import cz.amuradon.tralon.agent.connector.AccountBalance;

/**
 * Used for Websocket update message.
 */
public class MexcAccountBalanceUpdate implements AccountBalance {

	private final PrivateAccountV3Api privateAccount;
	
	public MexcAccountBalanceUpdate(PrivateAccountV3Api privateAccount) {
		this.privateAccount = privateAccount;
	}

	public String asset() {
		return privateAccount.getVcoinName();
	}
	
	public BigDecimal available() {
		return new BigDecimal(privateAccount.getBalanceAmount());
	}
	
	@Override
	public String toString() {
		return String.format("%s{%s}", getClass().getSimpleName(), privateAccount);
	}
}
