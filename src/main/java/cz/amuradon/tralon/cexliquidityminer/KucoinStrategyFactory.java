package cz.amuradon.tralon.cexliquidityminer;

import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.kucoin.sdk.KucoinPrivateWSClient;
import com.kucoin.sdk.KucoinPublicWSClient;
import com.kucoin.sdk.KucoinRestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class KucoinStrategyFactory {

	private final KucoinRestClient restClient;
    
    private final KucoinPublicWSClient wsClientPublic;
    
	private final KucoinPrivateWSClient wsClientPrivate;
	
	private final String baseToken;
	
	private final String quoteToken;
	
	private final BalanceMonitor balanceMonitor;
	
	private final BalanceHolder balanceHolder;
	
	private final ProducerTemplate producerTemplate;
	
	private final Map<String, Order> orders;
	
	private final OrderBook orderBook;
	
	@Inject
    public KucoinStrategyFactory(final KucoinRestClient restClient,
    		final KucoinPublicWSClient wsClientPublic,
    		final KucoinPrivateWSClient wsClientPrivate,
    		@ConfigProperty(name = "baseToken") String baseToken,
    		@ConfigProperty(name = "quoteToken") String quoteToken,
    		final BalanceMonitor balanceMonitor,
    		final BalanceHolder balanceHolder,
    		final ProducerTemplate producerTemplate,
    		final Map<String, Order> orders,
    		final OrderBook orderBook) {
		this.restClient = restClient;
		this.wsClientPublic = wsClientPublic;
		this.wsClientPrivate = wsClientPrivate;
		this.baseToken = baseToken;
		this.quoteToken = quoteToken;
		this.balanceMonitor = balanceMonitor;
		this.balanceHolder = balanceHolder;
		this.producerTemplate = producerTemplate;
		this.orders = orders;
		this.orderBook = orderBook;
    }
	
	public KucoinStrategy create() {
		return new KucoinStrategy(restClient, wsClientPublic, wsClientPrivate, baseToken, quoteToken,
				balanceMonitor, balanceHolder, producerTemplate, orders, orderBook);
	}
}
