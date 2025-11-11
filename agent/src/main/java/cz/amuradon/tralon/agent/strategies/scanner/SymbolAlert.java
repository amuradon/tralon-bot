package cz.amuradon.tralon.agent.strategies.scanner;

public record SymbolAlert(String symbol, String exchange, String timestamp) {

	public static final String CHANNEL = "symbolAlerts";
}
