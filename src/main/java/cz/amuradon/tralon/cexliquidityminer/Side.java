package cz.amuradon.tralon.cexliquidityminer;

public enum Side {
	BUY, SELL;

	public static Side getValue(String side) {
		return valueOf(side.toUpperCase());
	}
}
