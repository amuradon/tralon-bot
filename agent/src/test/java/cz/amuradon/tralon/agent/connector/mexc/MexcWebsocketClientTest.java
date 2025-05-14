package cz.amuradon.tralon.agent.connector.mexc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;

import cz.amuradon.tralon.agent.connector.RestClient;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.RemoteEndpoint.Basic;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

// TODO uz to neni JSON, ale PB
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MexcWebsocketClientTest {
	
	private static final String ORDER_UPDATE = 
	"""
	{"c":"spot@private.orders.v3.api","d":{"i":"C02__531314991060594689035","c":"ATNUSDT-00000195b290f7f0","o":1,
	"p":"0.5","v":"20","S":1,"a":"10","m":0,"A":"0","V":"0","lv":"20","s":2,"O":1742457601090,"ap":"0.2999","cv":"20",
	"ca":"5.998"},"s":"ATNUSDT","t":1742457601133}		
	""";

	@Mock
	private RestClient restClientMock;
	
	@Mock
	private WebSocketContainer webSocketContainerMock;
	
	@Mock
	private Session sessionMock;
	
	@Mock
	private Basic basicMock;
	
	private MockedStatic<ContainerProvider> containerProviderMock;
	
	private MexcWebsocketClient client;
	
	@BeforeEach
	public void prepare() throws Exception {
		 containerProviderMock = Mockito.mockStatic(ContainerProvider.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
		 containerProviderMock.when(ContainerProvider::getWebSocketContainer).thenReturn(webSocketContainerMock);
		 
		 when(webSocketContainerMock.connectToServer((Object) any(), any())).thenReturn(sessionMock);
		 when(sessionMock.getBasicRemote()).thenReturn(basicMock);
		 
		 client = new MexcWebsocketClient("someUri", restClientMock);
	}
	
	@AfterEach
	public void clean() {
		containerProviderMock.close();
	}
	
//	@Test
//	public void test() {
//		client.onOrderChange(o -> System.out.println(o));
//		client.onMessage(ORDER_UPDATE);
//	}
	
	@Test
	public void readPbFromFiles() throws Exception {
		FileInputStream input = new FileInputStream("C:\\work\\tralon\\data\\MEXC\\20250514\\BELUGAUSDT\\trades.pb");
		while (input.available() > 0) {
			System.out.println(PushDataV3ApiWrapper.parseDelimitedFrom(input));
		}
		
	}
}
