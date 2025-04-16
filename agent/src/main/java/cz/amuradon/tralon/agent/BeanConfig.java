package cz.amuradon.tralon.agent;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.connector.MyInputDecorator;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BeanConfig {

	@ClientObjectMapper
	static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
		System.out.println("*** Custom Object Mapper ***");
		return defaultObjectMapper.copyWith(new JsonFactoryBuilder().inputDecorator(new MyInputDecorator()).build());
	}
}
