package cz.amuradon.tralon.clm;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.agent.connector.MyInputDecorator;

public class JsonTest {

	@Test
	public void test() {
		ObjectMapper mapper = new ObjectMapper(new JsonFactoryBuilder().inputDecorator(new MyInputDecorator()).build());
	}
}
