package cz.amuradon.tralon.agent.strategies.scanner;

import java.math.BigDecimal;

public record VolumeData(BigDecimal average, BigDecimal lastVolume, BigDecimal lastVolume_1, BigDecimal lastVolume_2) {

    @Override
    public final String toString() {
        return String.format("avgVol: %s, vol-2: %s, vol-1: %s, vol: %s", average, lastVolume_2,
				lastVolume_1, lastVolume);
    }
}
