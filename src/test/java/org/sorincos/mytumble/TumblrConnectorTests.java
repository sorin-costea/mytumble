package org.sorincos.mytumble;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.tumblr.jumblr.JumblrClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = VertxBootApplication.class)
@ConfigurationProperties(prefix = "tumblr")
public class TumblrConnectorTests {

	private String username;

	private TumblrConnector tumblrConnVerticle;

	@Mock
	private Vertx vertx;

	@Mock
	private Context context;

	@Mock
	private EventBus eventBus;

	@Mock
	private JumblrClient client;

	@Mock
	private Future<Void> startFuture;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		when(vertx.eventBus()).thenReturn(eventBus);
		doReturn(context).when(vertx).getOrCreateContext();
		when(context.get("jumblrclient")).thenReturn(client);

		tumblrConnVerticle = new TumblrConnector();
		tumblrConnVerticle.init(vertx, context);

	}

	@After
	public void tearDown() throws Exception {
		if (tumblrConnVerticle != null) {
			tumblrConnVerticle.stop();
		}
	}

	@Test
	public void contextLoads() {
		Assert.assertNotNull(username);
	}

	@Test
	public void testStartValidConfig() throws Exception {
		JsonObject config = new JsonObject("{}");
		when(context.config()).thenReturn(config);

		tumblrConnVerticle.start(startFuture);

		verify(context, times(1)).config();
		verify(vertx, times(1)).eventBus();
		// verify(netClient, times(1)).connect(Matchers.eq(1234), Matchers.eq("foo"),
		// Matchers.<Handler<AsyncResult<NetSocket>>>any());
	}
}
