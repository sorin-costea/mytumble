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

	private String key;
	private String secret;
	private String oauthtoken;
	private String oauthpass;
	private String blogname;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public String getOauthtoken() {
		return oauthtoken;
	}

	public void setOauthtoken(String login) {
		this.oauthtoken = login;
	}

	public String getOauthpass() {
		return oauthpass;
	}

	public void setOauthpass(String password) {
		this.oauthpass = password;
	}

	public String getBlogname() {
		return blogname;
	}

	public void setBlogname(String blogname) {
		this.blogname = blogname;
	}

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
		if (tumblrConnVerticle != null)
			tumblrConnVerticle.stop();
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

	@Test
	public void loadUserDetails() {
		final String name = "o-noapte-de-aprilie";
		String avatar = "a";
		try {
			JumblrClient client = new JumblrClient(key, secret);
			client.setToken(oauthtoken, oauthpass);
			avatar = client.blogAvatar(name + ".tumblr.com");
			System.out.println("Getting avatar for " + name + ": " + avatar);
		} catch (Exception e) {
			System.out.println("Getting avatar for " + name + ": " + e.getLocalizedMessage());
		}
	}

}
