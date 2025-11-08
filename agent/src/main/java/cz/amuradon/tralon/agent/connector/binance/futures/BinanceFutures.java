package cz.amuradon.tralon.agent.connector.binance.futures;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface BinanceFutures {

	/**
     * To support inline instantiation of this qualifier.
     */
	public static final AnnotationLiteral<BinanceFutures> LITERAL = new AnnotationLiteral<>() {
		private static final long serialVersionUID = -7088966829173733889L;
		
	};
	
}
