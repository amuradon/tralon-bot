package cz.amuradon.tralon.agent.connector.mexc;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Mexc {

	/**
     * To support inline instantiation of this qualifier.
     */
	public static final AnnotationLiteral<Mexc> LITERAL = new AnnotationLiteral<>() {
		private static final long serialVersionUID = -1590190025720588338L;
	};
	
}
