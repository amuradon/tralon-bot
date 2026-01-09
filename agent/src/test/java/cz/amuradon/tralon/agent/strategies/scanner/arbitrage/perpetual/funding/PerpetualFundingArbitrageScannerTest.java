package cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cz.amuradon.tralon.agent.connector.FundingRate;
import cz.amuradon.tralon.agent.connector.PerpetualFundingRateRestClient;
import cz.amuradon.tralon.agent.strategies.scanner.arbitrage.perpetual.funding.PerpetualFundingArbitrageScanner.FundingRatesRow;
import io.smallrye.reactive.messaging.MutinyEmitter;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PerpetualFundingArbitrageScannerTest {
	
	@Mock
	private PerpetualFundingRateRestClient clientA;
	
	@Mock
	private PerpetualFundingRateRestClient clientB;
	
	@Mock
	private PerpetualFundingRateRestClient clientC;
	
	@Mock
	private ScheduledExecutorService schedulerMock;
	
	@Mock
	private MutinyEmitter<ChannelData> dataEmmitterMock;
	
	@Captor
	private ArgumentCaptor<ChannelData> channelDataCaptor;
	
	private List<FundingRate> listA;
	private List<FundingRate> listB;
	private List<FundingRate> listC;
	
	private long fundingTime;
	private long fundingTimeFar;
	
	private PerpetualFundingArbitrageScanner scanner;
	
	@BeforeEach
	public void prepare() {
		fundingTime = LocalDateTime.now(ZoneId.of("UTC")).plusHours(1).truncatedTo(ChronoUnit.HOURS)
				.toInstant(ZoneOffset.of("+0")).toEpochMilli();
		fundingTimeFar = LocalDateTime.now(ZoneId.of("UTC")).plusHours(2).truncatedTo(ChronoUnit.HOURS)
				.toInstant(ZoneOffset.of("+0")).toEpochMilli();

		listA = new ArrayList<>();
		listB = new ArrayList<>();
		listC = new ArrayList<>();
		
		when(clientA.exchangeName()).thenReturn("ExA");
		when(clientA.fundingRates()).thenReturn(listA);
		
		when(clientB.exchangeName()).thenReturn("ExB");
		when(clientB.fundingRates()).thenReturn(listB);
		
		when(clientC.exchangeName()).thenReturn("ExC");
		when(clientC.fundingRates()).thenReturn(listC);
		
		when(dataEmmitterMock.hasRequests()).thenReturn(true);
		
		scanner = new PerpetualFundingArbitrageScanner(Arrays.asList(clientA, clientB, clientC),
				dataEmmitterMock, schedulerMock);
	}
	
	static Stream<Arguments> spreadData() {
		return Stream.of(
				Arguments.of(new BigDecimal("1.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("0.5"), t.fundingTime));
					t.listB.add(new FundingRate("S", new BigDecimal("-0.5"), t.fundingTime));
					t.listC.add(new FundingRate("S", new BigDecimal("0.1"), t.fundingTime));
				} ),
				// Spread is 0.9 because positive rate can be always hedged on spot meaning 0% rate
				Arguments.of(new BigDecimal("0.9"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("0.9"), t.fundingTime));
					t.listC.add(new FundingRate("S", new BigDecimal("0.3"), t.fundingTime));
				} ),
				Arguments.of(new BigDecimal("0.7"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("-0.1"), t.fundingTime));
					t.listB.add(new FundingRate("S", new BigDecimal("-0.8"), t.fundingTime));
				} ),
				// No hedge for negative funding rate
				Arguments.of(new BigDecimal("0.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listB.add(new FundingRate("S", new BigDecimal("-1.5"), t.fundingTime));
				} ),
				// For positive funding rate, it can always be hedged on spot
				Arguments.of(new BigDecimal("1.5"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listB.add(new FundingRate("S", new BigDecimal("1.5"), t.fundingTime));
				} ),
				// First one farther in future represents 0% hedge - negative
				Arguments.of(new BigDecimal("2.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("-3.0"), t.fundingTimeFar));
					t.listB.add(new FundingRate("S", new BigDecimal("-2.0"), t.fundingTime));
					t.listC.add(new FundingRate("S", new BigDecimal("-1.0"), t.fundingTime));
				} ),
				// Non-first one farther in future represents 0% hedge - negative
				Arguments.of(new BigDecimal("2.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("-2.0"), t.fundingTime));
					t.listB.add(new FundingRate("S", new BigDecimal("-3.0"), t.fundingTimeFar));
					t.listC.add(new FundingRate("S", new BigDecimal("-1.0"), t.fundingTime));
				} ),
				// First one farther in future represents 0% hedge - positive
				Arguments.of(new BigDecimal("2.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("3.0"), t.fundingTimeFar));
					t.listB.add(new FundingRate("S", new BigDecimal("2.0"), t.fundingTime));
					t.listC.add(new FundingRate("S", new BigDecimal("1.0"), t.fundingTime));
				} ),
				// Non-first one farther in future represents 0% hedge - positive
				Arguments.of(new BigDecimal("2.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("2.0"), t.fundingTime));
					t.listB.add(new FundingRate("S", new BigDecimal("3.0"), t.fundingTimeFar));
					t.listC.add(new FundingRate("S", new BigDecimal("1.0"), t.fundingTime));
				} ),
				// Symbol with funding in future only should be filtered out
				Arguments.of(new BigDecimal("2.0"), (Consumer<PerpetualFundingArbitrageScannerTest>) t -> {
					t.listA.add(new FundingRate("S", new BigDecimal("2.0"), t.fundingTime));
					t.listA.add(new FundingRate("X", new BigDecimal("1.0"), t.fundingTimeFar));
				} )
		);
	}
	
	@ParameterizedTest
	@MethodSource("spreadData")
	public void spread(BigDecimal expectedDiff, Consumer<PerpetualFundingArbitrageScannerTest> prepareData) {
		prepareData.accept(this);
		
		scanner.scan();
		
		verify(dataEmmitterMock).sendAndForget(channelDataCaptor.capture());
		
		List<FundingRatesRow> rows = channelDataCaptor.getValue().sortedRows();
		
		Assertions.assertEquals(1, rows.size());
		Assertions.assertEquals("S", rows.get(0).symbol());
		Assertions.assertEquals(expectedDiff, rows.get(0).diff());
	}

}
