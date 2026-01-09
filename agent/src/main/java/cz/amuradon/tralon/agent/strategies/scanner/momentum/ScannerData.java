package cz.amuradon.tralon.agent.strategies.scanner.momentum;

import java.util.List;

public record ScannerData(String exchange, List<ScannerDataItem> data) {

	public static final String CHANNEL = "scannerData";
}
