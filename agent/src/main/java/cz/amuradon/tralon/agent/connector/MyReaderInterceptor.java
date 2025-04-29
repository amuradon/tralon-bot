package cz.amuradon.tralon.agent.connector;

import java.io.IOException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

public class MyReaderInterceptor implements ReaderInterceptor {

	/*
	 * TODO
	 * - da se neco nainjectovat?
	 *   - ExecutorService
	 *   - DataPathProvider
	 * - rozpoznat metodu -> soubor
	 *   - Nelze obecne podle navratoveho typu
	 *   - Nelze podle URL path
	 *   - Nutno pouzit vlastni anotaci nebo se nejak dostat k nazvu volane metody
	 */
	
	@Override
	public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
		System.out.println("**** MyReaderInterceptor");
		context.getInputStream();
		context.getGenericType();
		return context.proceed();
	}

}
