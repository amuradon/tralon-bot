package cz.amuradon.tralon.clm.connector.binance;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Binance {

	/**
     * To support inline instantiation of this qualifier.
     */
	public static final AnnotationLiteral<Binance> LITERAL = new AnnotationLiteral<>() {
		private static final long serialVersionUID = -1590190025720588338L;
	};
	
}
