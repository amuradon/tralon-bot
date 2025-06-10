package cz.amuradon.tralon.agent.strategies.newlisting;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.SymbolInfo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FixedPercentClosePositionUpdatesProcessorTest {
	
	private static final String SYMBOL = "TKNUSDT";

	@Mock(answer = Answers.RETURNS_SELF)
	private RestClient.NewOrderBuilder newOrderBuilderMock;
	
	@Mock
	private RestClient.NewOrderSymbolBuilder newOrderSymbolBuilderMock;
	
	@Mock
	private RestClient restClientMock;
	
	@Captor
	private ArgumentCaptor<BigDecimal> priceCaptor;
	
	private FixedPercentClosePositionUpdatesProcessor processor;
	
	@BeforeEach
	public void prepare() {
		when(newOrderSymbolBuilderMock.symbol(anyString())).thenReturn(newOrderBuilderMock);
		when(restClientMock.newOrder()).thenReturn(newOrderSymbolBuilderMock);
		
		processor = new FixedPercentClosePositionUpdatesProcessor(restClientMock, SYMBOL, 15, 20, 1, 1);
	}
	
	@Test
	public void takeProfitPriceCalculation() {
		processor.symbolInfo = new SymbolInfo(3);
		processor.baseQuantity = BigDecimal.ONE;
		processor.actualBuyPrice = new BigDecimal("100.000");
		processor.initialBuyOrderDone();
		
		verify(newOrderBuilderMock).price(priceCaptor.capture());
		verify(newOrderBuilderMock).send();
		
		BigDecimal price = priceCaptor.getValue();
		Assertions.assertEquals(new BigDecimal("115.000"), price);
	}
}
