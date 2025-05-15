package cz.amuradon.tralon.agent.connector.mexc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
			if (path.contains("?")) {
				String[] parts = path.split("\\?");
				if (parts[0].equals("/order")) {
					Matcher matcher = Pattern.compile(".*&(newClientOrderId=\\w+)&.*").matcher(parts[1]);
					if (matcher.find()) {
						return parts[0] + "?" + matcher.group(1);
					} else {
						return parts[0];
					}
				} else {
					return parts[0];
				}
			} else {
				return path;
			}
			
		});
		return super.dispatch(requestSpy);
	}
}
