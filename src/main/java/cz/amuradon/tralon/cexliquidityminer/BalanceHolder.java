package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BalanceHolder {

	private BigDecimal baseBalance;
	    
	private BigDecimal quoteBalance;
	  
	private AtomicInteger waitForBalanceUpdate;
	
	public BalanceHolder() {
		baseBalance = BigDecimal.ZERO;
	    quoteBalance = BigDecimal.ZERO;
		waitForBalanceUpdate = new AtomicInteger(0);
	}
	
	public BigDecimal getBaseBalance() {
		return baseBalance;
	}

	public void setBaseBalance(BigDecimal baseBalance) {
		this.baseBalance = baseBalance;
	}

	public BigDecimal getQuoteBalance() {
		return quoteBalance;
	}

	public void setQuoteBalance(BigDecimal quoteBalance) {
		this.quoteBalance = quoteBalance;
	}

	public AtomicInteger getWaitForBalanceUpdate() {
		return waitForBalanceUpdate;
	}
}
