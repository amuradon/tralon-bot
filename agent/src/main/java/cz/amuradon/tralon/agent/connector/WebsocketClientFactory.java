package cz.amuradon.tralon.agent.connector;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * The qualifier annotation to workaround {@link Instance} strange behavior requiring {@link Default}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface WebsocketClientFactory {

	/**
     * To support inline instantiation of this qualifier.
     */
	public static final AnnotationLiteral<WebsocketClientFactory> LITERAL = new AnnotationLiteral<>() {
		private static final long serialVersionUID = 5962887391487303607L;
	};
}
