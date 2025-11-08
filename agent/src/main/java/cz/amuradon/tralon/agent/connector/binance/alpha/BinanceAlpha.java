package cz.amuradon.tralon.agent.connector.binance.alpha;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface BinanceAlpha {

	/**
     * To support inline instantiation of this qualifier.
     */
	public static final AnnotationLiteral<BinanceAlpha> LITERAL = new AnnotationLiteral<>() {
		private static final long serialVersionUID = -4246564398829011738L;
	};
	
}
