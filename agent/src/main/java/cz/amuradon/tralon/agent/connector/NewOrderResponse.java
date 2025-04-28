package cz.amuradon.tralon.agent.connector;

public record NewOrderResponse(boolean success, String orderId, NewOrderError error) {

}
