package cz.amuradon.tralon.cexliquiditymining;

import java.math.BigDecimal;

public class PriceProposal {

	public BigDecimal currentPrice = BigDecimal.ZERO;
    
    public BigDecimal proposedPrice = BigDecimal.ZERO;
    
    public long timestamp;
    
    @Override
    public String toString() {
    	return String.format("%s(currentPrice=%s, proposedPrice=%s, timestamp=%d)",
    			PriceProposal.class.getSimpleName(), currentPrice, proposedPrice, timestamp);
    }
}
