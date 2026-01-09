package cz.amuradon.tralon.agent.strategies.scanner.momentum;

public record ScannerDataItem(String symbol, String exchange, String link, String timestamp,
		boolean isNew, String data, String tradingViewLink) {

}
