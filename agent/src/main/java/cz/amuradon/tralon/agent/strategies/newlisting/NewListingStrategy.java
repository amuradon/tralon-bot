package cz.amuradon.tralon.agent.strategies.newlisting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import cz.amuradon.tralon.agent.OrderStatus;
import cz.amuradon.tralon.agent.OrderType;
import cz.amuradon.tralon.agent.Side;
import cz.amuradon.tralon.agent.connector.OrderBookResponse;
import cz.amuradon.tralon.agent.connector.OrderBookUpdate;
import cz.amuradon.tralon.agent.connector.OrderChange;
import cz.amuradon.tralon.agent.connector.RestClient;
import cz.amuradon.tralon.agent.connector.SymbolInfo;
import cz.amuradon.tralon.agent.connector.Trade;
import cz.amuradon.tralon.agent.connector.WebsocketClient;
import cz.amuradon.tralon.agent.strategies.Strategy;
import io.quarkus.logging.Log;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class NewListingStrategy implements Strategy {

	// TODO move MEXC error codes to connector
	private static final String NOT_YET_TRADING_ERR_CODE = "30001";
	
	private static final String ORDER_PRICE_ABOVE_LIMIT_ERR_CODE = "30010";

	private static final String TIME_PROP_NAME = "time";

	private final ScheduledExecutorService scheduler;
	
	private final RestClient restClient;
	
	private final WebsocketClient websocketClient;
	
	private final ComputeInitialPrice computeInitialPrice;
	
    private final BigDecimal maxQuoteBalanceToUse;
    private final String symbol;
    
    private final int buyOrderRequestsPerSecond;
	private final int buyOrderMaxAttempts;
    
//    private final Path dataDir;
    
    private final int listingHour;
    
    private final int listingMinute;
    
    private final int trailingStopBelow;

	private final int trailingStopDelayMs;
	
	private final int initialBuyOrderDelayMs;
	
//	private final Path tradeUpdatesFilePath;
//	
//	private final Path depthUpdatesFilePath; 
	
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
    		final RestClient restClient,
    		final WebsocketClient websocketClient,
    		final ComputeInitialPrice computeInitialPrice,
    		final BigDecimal maxQuoteBalanceToUse,
    		final String symbol,
    		final LocalDateTime listingDateTime,
//    		@Named(BeanConfig.DATA_DIR) final Path dataDir,
    		@ConfigProperty(name = "buyOrder.requestsPerSecond") final int buyOrderRequestsPerSecond,
    		@ConfigProperty(name = "buyOrder.maxAttempts") final int buyOrderMaxAttempts,
    		@ConfigProperty(name = "trailingStop.below") final int trailingStopBelow,
			@ConfigProperty(name = "trailingStop.delayMs") final int trailingStopDelayMs,
			@ConfigProperty(name = "initialBuyOrder.delayMs") final int initialBuyOrderDelayMs) {
    	
		scheduler = Executors.newScheduledThreadPool(2);
		this.restClient = restClient;
		this.websocketClient = websocketClient;
		this.computeInitialPrice = computeInitialPrice;
    	this.maxQuoteBalanceToUse = maxQuoteBalanceToUse;
    	this.symbol = symbol;
    	this.buyOrderRequestsPerSecond = buyOrderRequestsPerSecond;
    	this.buyOrderMaxAttempts = buyOrderMaxAttempts;
//    	this.dataDir = dataDir;
    	this.trailingStopBelow = trailingStopBelow;
		this.trailingStopDelayMs = trailingStopDelayMs;
		this.initialBuyOrderDelayMs = initialBuyOrderDelayMs;
    	
    	listingHour = listingDateTime.getHour();
    	listingMinute = listingDateTime.getMinute();
    }
	
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
		
		LocalDateTime beforeStart = LocalDateTime.now().withHour(startHour).withMinute(startMinute).withSecond(50);
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
		
		try {
			prepareTask.get();
			placeNewBuyOrderTask.get();
		} catch (Exception e) {
			throw new RuntimeException("The execution failed", e);
		}

	}

	@Override
	public void onOrderBookUpdate(OrderBookUpdate update, Map<BigDecimal, BigDecimal> orderBookSide) {
		// Do nothing
	}

	@Override
	public void onBaseBalanceUpdate(BigDecimal balance) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onQuoteBalanceUpdate(BigDecimal balance) {
		// TODO Auto-generated method stub

	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

    // XXX Temporary testing
//    @Startup
	public void prepare() {
		/*
		 * TODO
		 * - When application starts delete all existing listenKeys
		 *   - I can't there might be more agents running at the same time
		 * - Every 60 minutes sent keep alive request
		 * - After 24 hours reconnect - create a new listen key?
		 */
		
		// subscribe updates
		symbolInfo = restClient.cacheSymbolDetails(symbol);
		websocketClient.onTrade(this::processTradeUpdate, symbol);
		websocketClient.onOrderChange(this::processOrderUpdate);
		
		// get order book
//		String exchangeInfoJson = restClient.exchangeInfo();
		OrderBookResponse orderBookResponse = restClient.orderBook(symbol);

		// TODO how to collect those data as well? Connector decorator? How to store in files again.
//		try {
//			Files.writeString(dataDir.resolve("exchangeInfo.json"), exchangeInfoJson, StandardOpenOption.CREATE);
//			Files.writeString(dataDir.resolve("depth.json"), orderBookResponse.toString(), StandardOpenOption.CREATE);
//		} catch (IOException e) {
//			throw new IllegalStateException("Could write JSON to files", e);
//		}
		

//		try {
//			ExchangeInfo exchangeInfo = mapper.readValue(exchangeInfoJson, ExchangeInfo.class);
//			OrderBook orderBook = mapper.readValue(orderBookJson, OrderBook.class);
			
			initialBuyPrice = computeInitialPrice.execute(symbol, orderBookResponse);
			Log.infof("Computed buy limit order price: %s", initialBuyPrice);
//		} catch (JsonProcessingException e) {
//			throw new IllegalStateException("JSON could not be parsed", e);
//		}
		
		// XXX temporary testing
//		placeNewBuyOrder();
	}
    
	// XXX Opened for component test for now
	public void placeNewBuyOrder() {
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
			.clientOrderId(clientOrderId)
			.symbol(symbol)
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
					String orderId = newOrderBuilder.send();
					buyOrderId = orderId;
					Log.infof("New order placed: %s", orderId);
					break;
				} catch (WebApplicationException e) {
					Response response = e.getResponse();
					ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
					int status = response.getStatus();
					Log.errorf("ERR response: %d - %s: %s, Headers: %s", status,
							response.getStatusInfo().getReasonPhrase(), errorResponse, response.getHeaders());
					if (ORDER_PRICE_ABOVE_LIMIT_ERR_CODE.equalsIgnoreCase(errorResponse.code())) {
						Matcher matcher = Pattern.compile(".*\\s(\\d+(\\.\\d+)?)USDT").matcher(errorResponse.msg());
						if (matcher.find()) {
							String maxPrice = matcher.group(1);
							Log.infof("Resetting max price: '%s'", maxPrice);
							timestamp = currentTime;
							newOrderBuilder.timestamp(timestamp).price(new BigDecimal(maxPrice)).signParams();
						}
					} else if (status == 429) {
						Log.warnf("Retry after: ", response.getHeaderString("Retry-After"));
						// Do nothing, repeat
					} else if (!NOT_YET_TRADING_ERR_CODE.equalsIgnoreCase(errorResponse.code())) {
						Log.infof("It is not \"Not yet trading\" error code '%s', not retrying...", NOT_YET_TRADING_ERR_CODE);
						break;
					}
				}
			}
		}
	}
	
	private void processTradeUpdate(Trade trade) {
		if (positionOpened) {
			BigDecimal price = trade.price();
			if (price.compareTo(maxPrice) > 0) {
				maxPrice = trade.price();
				stopPrice = maxPrice.multiply(new BigDecimal(100 - trailingStopBelow))
						.divide(new BigDecimal(100), symbolInfo.priceScale(), RoundingMode.DOWN);
			}
			
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
