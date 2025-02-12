package cz.amuradon.tralon.cexliquidityminer;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyRouteBuilder extends EndpointRouteBuilder {

	public static final String DIRECT_ORDER_BOOK_SNAPSHOT = "direct:orderBookSnapshot";

	public static final String ROUTE_ID_LEVEL2_MARKET_UPDATE = "level2MarketUpdate";
	
	public static final String SEDA_LEVEL2_MARKET_UPDATE = "seda:" + ROUTE_ID_LEVEL2_MARKET_UPDATE;
	
	
	@Override
	public void configure() throws Exception {
		from(DIRECT_ORDER_BOOK_SNAPSHOT)
			.bean(OrderBookManager.BEAN_NAME, "processOrderBook")
			.to("controlbus:route?routeId=" + ROUTE_ID_LEVEL2_MARKET_UPDATE + "&action=start");
		
		from(SEDA_LEVEL2_MARKET_UPDATE)
			.routeId(ROUTE_ID_LEVEL2_MARKET_UPDATE)
			.autoStartup(false)
			.bean(OrderBookManager.BEAN_NAME, "processUpdate");
		
	}

}
