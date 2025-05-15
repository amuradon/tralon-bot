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

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.NoValidTradePriceException;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RequestException;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.SymbolInfo;
import cz.amuradon.tralon.agent.connector.Trade;
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
    
    private final int trailingStopBelow;

	private final int trailingStopDelayMs;
	
	private final int initialBuyOrderDelayMs;
	
	private boolean initialBuyValid = true;
	
	private boolean positionOpened = false;
	
	private long buyOrderPriceOverTimestamp = Long.MAX_VALUE;
	
	private BigDecimal baseQuantity;
	
	private BigDecimal maxPrice = BigDecimal.ZERO;
	private BigDecimal stopPrice = BigDecimal.ZERO;
	private long lastStopPriceDrop = Long.MAX_VALUE;
	
	private String buyOrderId;
	
	private String buyClientOrderId;
	
	private SymbolInfo symbolInfo;
	
	private BigDecimal initialBuyPrice;
    
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
    		final int trailingStopBelow,   // XXX should be BigDecimal
			final int trailingStopDelayMs,
			final int initialBuyOrderDelayMs) {
		this.scheduler = scheduler;
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.computeInitialPrice = computeInitialPrice;
    	this.maxQuoteBalanceToUse = maxQuoteBalanceToUse;
    	this.symbol = symbol;
    	this.buyOrderRequestsPerSecond = buyOrderRequestsPerSecond;
    	this.buyOrderMaxAttempts = buyOrderMaxAttempts;
    	this.trailingStopBelow = trailingStopBelow;
		this.trailingStopDelayMs = trailingStopDelayMs;
		this.initialBuyOrderDelayMs = initialBuyOrderDelayMs;
    	
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
		symbolInfo = restClient.cacheSymbolDetails(symbol);
		websocketClient.onTrade(this::processTradeUpdate, symbol);
		websocketClient.onOrderChange(this::processOrderUpdate);
		websocketClient.onAccountBalance(b -> Log.infof("Account balance update: %s", b));
		
		// XXX Subscribe to store to file
		websocketClient.onOrderBookChange(c -> {}, symbol);
		
		// get order book
		OrderBookResponse orderBookResponse = restClient.orderBook(symbol);

		initialBuyPrice = computeInitialPrice.execute(symbol, orderBookResponse);
		Log.infof("Computed buy limit order price: %s", initialBuyPrice);
	}
    
	private void placeNewBuyOrder() {
		// TODO kdyz price jeste neni nasetovana metodou vyse - muze se stat, je to async
	
		String clientOrderId = symbol + "-" + HexFormat.of().toHexDigits(new Date().getTime());
		Log.infof("Client Order ID: %s", clientOrderId);
		LocalDateTime now = LocalDateTime.now();
		BigDecimal price = initialBuyPrice;
		buyClientOrderId = clientOrderId;
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
					buyOrderId = newOrderBuilder.send();
					Log.infof("New order placed: %s", buyOrderId);
					break;
				} catch (NoValidTradePriceException e) {
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
	
	private void processTradeUpdate(Trade trade) {
		BigDecimal price = trade.price();
		if (price.compareTo(maxPrice) > 0) {
			maxPrice = trade.price();
			stopPrice = maxPrice.multiply(new BigDecimal(100 - trailingStopBelow))
					.divide(new BigDecimal(100), symbolInfo.priceScale(), RoundingMode.DOWN);
		}

		if (positionOpened) {
			
			// Caution: market order does not work in first (one?) minute, it is immediately cancelled
			if (price.compareTo(stopPrice) <= 0) {
				if (trade.timestamp() - lastStopPriceDrop > trailingStopDelayMs) {
					restClient.newOrder()
						.symbol(symbol)
						.side(Side.SELL)
						.type(OrderType.LIMIT)
						.size(baseQuantity)
						// Emulate market order to set lowest possible limit price
						.price(BigDecimal.ONE.scaleByPowerOfTen(symbolInfo.priceScale()))
						.send();
					positionOpened = false;
				} else {
					lastStopPriceDrop = trade.timestamp();
				}
			} else {
				lastStopPriceDrop = Long.MAX_VALUE;
			}
			
			// If there is still at least partial of initial buy order
			if (initialBuyValid) {
				if (trade.price().compareTo(initialBuyPrice) > 0) {
					buyOrderPriceOverTimestamp = Math.min(buyOrderPriceOverTimestamp, trade.timestamp());
					if (System.currentTimeMillis() - buyOrderPriceOverTimestamp > initialBuyOrderDelayMs) {
						restClient.cancelOrder(buyOrderId, symbol);
						initialBuyValid = false;
					}
				} else {
					buyOrderPriceOverTimestamp = Long.MAX_VALUE;
				}
			}
		}
	}
	
	private void processOrderUpdate(OrderChange orderChange) {
		Log.infof("Order update: %s", orderChange);
		if (orderChange.clientOrderId().equalsIgnoreCase(buyClientOrderId)){
			if (orderChange.status() == OrderStatus.PARTIALLY_FILLED) {
				positionOpened = true;
				baseQuantity = orderChange.cumulativeQuantity();
			} else if (orderChange.status() == OrderStatus.FILLED) {
				positionOpened = true;
				initialBuyValid = false;
				baseQuantity = orderChange.cumulativeQuantity();
			} else if (orderChange.status() == OrderStatus.CANCELED) {
				initialBuyValid = false;
			}
		 }
	}
}
