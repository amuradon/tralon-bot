package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.NoValidTradePriceException;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.RequestException;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.TradeDirectionNotAllowedException;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.strategies.Strategy;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;

public class NewListingStrategy implements Strategy {

	private final ScheduledExecutorService scheduler;
	
	private final RestClient restClient;
	
	private final WebsocketClient websocketClient;
	
	private final ComputeInitialPrice computeInitialPrice;
	
    private final BigDecimal maxQuoteBalanceToUse;
    private final String symbol;
    
    private final int buyOrderRequestsPerSecond;
	private final int buyOrderMaxAttempts;
    
    private final int listingHour;
    
    private final int listingMinute;
    
	private BigDecimal initialBuyPrice;
	
	private final UpdatesProcessor updatesProcessor;
    
    public NewListingStrategy(
    		final ScheduledExecutorService scheduler,
    		final RestClient restClient,
    		final WebsocketClient websocketClient,
    		final ComputeInitialPrice computeInitialPrice,
    		final BigDecimal maxQuoteBalanceToUse,
    		final String symbol,
    		final LocalDateTime listingDateTime,
    		final int buyOrderRequestsPerSecond,
    		final int buyOrderMaxAttempts,
			final UpdatesProcessor updatesProcessor) {
		this.scheduler = scheduler;
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.computeInitialPrice = computeInitialPrice;
    	this.maxQuoteBalanceToUse = maxQuoteBalanceToUse;
    	this.symbol = symbol;
    	this.buyOrderRequestsPerSecond = buyOrderRequestsPerSecond;
    	this.buyOrderMaxAttempts = buyOrderMaxAttempts;
		this.updatesProcessor = updatesProcessor;
    	
    	listingHour = listingDateTime.getHour();
    	listingMinute = listingDateTime.getMinute();
    }
    
    /*
     * FIXME
     * - kdyz se upravila cena na novy max, selhal signature
     * - websocket sessions se zdaji byt oddelene - 2 tokeny soucasne
     * - nez se strategie rozjede, neni vypsana v seznamu
     * - kdyz strategii pustim, mel by se vycistit formular
     */
	
	@Override
	public void start() {
		int startHour = listingHour;
		int startMinute = listingMinute;
    		
		if (startMinute == 0) {
			startHour--;
			startMinute = 59;
		} else {
			startMinute--;
		}

		Log.infof("Listing at %d:%d", listingHour, listingMinute);
		Log.infof("Agent starts at %d:%d", startHour, startMinute);
		
		LocalDateTime beforeStart = LocalDateTime.now().withHour(startHour).withMinute(startMinute)
				.withSecond(50).withNano(0);
		Log.infof("Listing start: %s", beforeStart);
		LocalDateTime now = LocalDateTime.now();
		
		if (now.isAfter(beforeStart)) {
			throw new IllegalStateException(String.format("The start time '%s' is in past", beforeStart));
		}
		
		ScheduledFuture<?> prepareTask = scheduler.schedule(this::prepare, Math.max(0, now.until(beforeStart, ChronoUnit.SECONDS)),
				TimeUnit.SECONDS);
		ScheduledFuture<?> placeNewBuyOrderTask = scheduler.schedule(this::placeNewBuyOrder,
				Math.max(0, now.until(beforeStart.withSecond(59).withNano(980000000), ChronoUnit.MILLIS)),
				TimeUnit.MILLISECONDS);
		
		// FIXME tohle nefunguje spravne, pak se mi nezobrazuji running strategies -> vyextrahovat scheduling mimo
		try {
			prepareTask.get();
			placeNewBuyOrderTask.get();
		} catch (Exception e) {
			throw new RuntimeException("The execution failed", e);
		}

	}

	@Override
	public void stop() {
		// TODO Cancel all
		websocketClient.close();
	}

	@Override
	public String getDescription() {
		return String.format("%s{%s, %s}", getClass().getSimpleName(), restClient.getClass().getSimpleName(), symbol);
	}

	private void prepare() {
		/*
		 * TODO
		 * - Validace, zda dany asset existuje
		 * - When application starts delete all existing listenKeys
		 *   - I can't there might be more agents running at the same time
		 * - Every 60 minutes sent keep alive request
		 * - After 24 hours reconnect - create a new listen key?
		 */
		
		// subscribe updates
		updatesProcessor.querySymbolInfo();
		websocketClient.onTrade(updatesProcessor::processTradeUpdate, symbol);
		websocketClient.onOrderChange(updatesProcessor::processOrderUpdate);
		websocketClient.onAccountBalance(b -> Log.infof("Account balance update: %s", b));
		
		// XXX Subscribe to store to file
		websocketClient.onOrderBookChange(c -> {}, symbol);
		
		// get order book
		OrderBookResponse orderBookResponse = restClient.orderBook(symbol);

		initialBuyPrice = computeInitialPrice.execute(symbol, orderBookResponse);
		updatesProcessor.setInitialBuyPrice(initialBuyPrice);
		Log.infof("Computed buy limit order price: %s", initialBuyPrice);
	}
    
	private void placeNewBuyOrder() {
		// TODO kdyz price jeste neni nasetovana metodou vyse - muze se stat, je to async
	
		String clientOrderId = symbol + "-" + HexFormat.of().toHexDigits(new Date().getTime());
		Log.infof("Client Order ID: %s", clientOrderId);
		LocalDateTime now = LocalDateTime.now();
		BigDecimal price = initialBuyPrice;
		updatesProcessor.setClientOrderId(clientOrderId);
		long timestamp = LocalDateTime.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
				listingHour, listingMinute)
			.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli();
		// XXX Temporary testing
//		long timestamp = new Date().getTime();
		
		int recvWindow = 60000;
		RestClient.NewOrderBuilder newOrderBuilder = restClient.newOrder()
			.symbol(symbol)
			.clientOrderId(clientOrderId)
			.side(Side.BUY)
			.type(OrderType.LIMIT)
			// FIXME quantity je spocitana na tu max price!!!
			.size(maxQuoteBalanceToUse.divide(price, 10, RoundingMode.HALF_UP))
			.price(price)
			.recvWindow(recvWindow)
			.timestamp(timestamp)
			.signParams();
		
		// TODO muze byt az sem vsechno udelano dopredu a tady pockat na spravny cas otevreni burzy?

		long previousSendTime = 0;
		long msPerRequest = Math.round(Math.ceil(1000.0 / buyOrderRequestsPerSecond));
		
		for (int i = 0; i < buyOrderMaxAttempts;) {
			long currentTime = System.currentTimeMillis();
			if (currentTime - timestamp >= recvWindow) {
				timestamp = currentTime;
				newOrderBuilder.timestamp(timestamp).signParams();
			}
			if (currentTime - previousSendTime > msPerRequest) {
				i++;
				previousSendTime = currentTime; 
				Log.infof("Place new buy limit order attempt %d", i);
				
				try {
					String buyOrderId = newOrderBuilder.send();
					updatesProcessor.setBuyOrderId(buyOrderId);
					Log.infof("New order placed: %s", buyOrderId);
					break;
				} catch (NoValidTradePriceException e) {
					// XXX pri zmene max ceny by to melo prepocitat i mnozstvi 
					BigDecimal maxPrice = e.validPrice();
					Log.infof("Resetting max price: '%s'", maxPrice);
					timestamp = currentTime;
					newOrderBuilder.timestamp(timestamp).price(maxPrice).signParams();
					// Repeat
				} catch (TradeDirectionNotAllowedException e) {
					// Do nothing, repeat
				} catch (RequestException e) {
					Response response = e.getResponse();
					ErrorResponse errorResponse = e.errorResponse();
					int status = response.getStatus();
					Log.errorf("ERR response: %d - %s: %s, Headers: %s", status,
							response.getStatusInfo().getReasonPhrase(), errorResponse, response.getHeaders());
					if (status == 429) {
						Log.warnf("Retry after: ", response.getHeaderString("Retry-After"));
						// Do nothing, repeat
					} else {
						Log.infof("It is not \"Not yet trading\" error code '%s - %s', not retrying...",
								errorResponse.code(), errorResponse.msg());
						break;
					}
				}
			}
		}
	}
}
