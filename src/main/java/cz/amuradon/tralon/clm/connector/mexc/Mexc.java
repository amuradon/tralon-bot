package cz.amuradon.tralon.clm.connector.mexc;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.quarkus.arc.All.Literal;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Mexc {

	/**
     * Supports inline instantiation of this qualifier.
     */
    public static final class MexcLiteral extends AnnotationLiteral<Mexc> implements Mexc {

        public static final MexcLiteral INSTANCE = new MexcLiteral();

        private static final long serialVersionUID = 1L;

    }
}
