package cz.amuradon.tralon.cexliquidityminer;

import java.math.BigDecimal;

public class BalanceHolder {

	private BigDecimal baseBalance;
	    
	private BigDecimal quoteBalance;
	  
	public BalanceHolder() {
		baseBalance = BigDecimal.ZERO;
	    quoteBalance = BigDecimal.ZERO;
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

	public BalanceHolder clone() {
		BalanceHolder holder = new BalanceHolder();
		holder.baseBalance = baseBalance;
		holder.quoteBalance = quoteBalance;
		return holder;
	}
}
