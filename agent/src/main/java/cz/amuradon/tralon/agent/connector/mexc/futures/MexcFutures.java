package cz.amuradon.tralon.agent.connector.mexc.futures;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface MexcFutures {

	/**
     * To support inline instantiation of this qualifier.
     */
	public static final AnnotationLiteral<MexcFutures> LITERAL = new AnnotationLiteral<>() {
		private static final long serialVersionUID = -7088966829173733889L;
		
	};
	
}
