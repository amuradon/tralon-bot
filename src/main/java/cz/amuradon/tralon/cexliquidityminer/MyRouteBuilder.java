package cz.amuradon.tralon.cexliquidityminer;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyRouteBuilder extends EndpointRouteBuilder {

	public static final String DIRECT_START_L2_MARKET_UPDATE_ROUTE = "direct:startL2MarketUpdateRoute";

	public static final String LEVEL2_MARKET_UPDATE = "level2MarketUpdate";
	
	public static final String SEDA_LEVEL2_MARKET_UPDATE = "seda:" + LEVEL2_MARKET_UPDATE;
	
	private static final String CANCEL_ORDERS = "cancelOrders";
	
	public static final String SEDA_CANCEL_ORDERS = "seda:" + CANCEL_ORDERS;
	
	private static final String PLACE_NEW_ORDERS = "placeNewOrders";

	public static final String SEDA_PLACE_NEW_ORDERS = "seda:" + PLACE_NEW_ORDERS;
	
	@Override
	public void configure() throws Exception {
		// XXX How to start it through Camel Context not using one-time route
		from(DIRECT_START_L2_MARKET_UPDATE_ROUTE)
			.to("controlbus:route?routeId=" + LEVEL2_MARKET_UPDATE + "&action=start");
		
		from(SEDA_LEVEL2_MARKET_UPDATE)
			.routeId(LEVEL2_MARKET_UPDATE)
			.autoStartup(false)
			.bean(OrderBookManager.BEAN_NAME, "processUpdate");
		
		from(SEDA_CANCEL_ORDERS)
			.routeId(CANCEL_ORDERS)
			.bean(CancelOrders.BEAN_NAME);
		
		from(SEDA_PLACE_NEW_ORDERS)
			.routeId(PLACE_NEW_ORDERS)
			.bean(PlaceNewOrders.BEAN_NAME);
		
	}

}
