package cz.amuradon.tralon.agent.connector.mexc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;
import java.util.Queue;

import org.mockito.Answers;
import org.mockito.MockMakers;

import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.http.MockResponse;
import io.fabric8.mockwebserver.http.RecordedRequest;
import io.fabric8.mockwebserver.internal.MockDispatcher;

public class UrlParamsIgnoringDispatcher extends MockDispatcher {

	public UrlParamsIgnoringDispatcher(Map<ServerRequest, Queue<ServerResponse>> responses) {
		super(responses);
	}

	@Override
	public MockResponse dispatch(RecordedRequest request) {
		// It still cannot spy final class but it can mock it
		RecordedRequest requestSpy = mock(RecordedRequest.class, withSettings().mockMaker(MockMakers.INLINE)
				.spiedInstance(request).defaultAnswer(Answers.CALLS_REAL_METHODS));
		when(requestSpy.getPath()).thenAnswer(i -> {
			String path = request.getPath();
			return  path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
		});
		return super.dispatch(requestSpy);
	}
}
