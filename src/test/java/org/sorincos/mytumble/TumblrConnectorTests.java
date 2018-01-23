package org.sorincos.mytumble;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.User;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

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
	@Ignore
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

	@Test
	public void loadFollowers() {
		try {
			JumblrClient client = new JumblrClient(key, secret);
			client.setToken(oauthtoken, oauthpass);
			System.out.println("Blog: " + client.user().getBlogs().get(0).getName() + " has followers: "
			        + client.user().getBlogs().get(0).getFollowersCount());
			List<User> followers = client.user().getBlogs().get(0).followers();
			int offset = 10;
			while (!followers.isEmpty()) {
				System.out.println("--------- offset: " + offset);
				followers.forEach(user -> System.out.println(user.getName()));
				Map<String, String> options = new HashMap<String, String>();
				options.put("offset", Integer.toString(offset));
				offset += 10;
				followers = client.user().getBlogs().get(0).followers(options);
				Thread.sleep(200);
			}
		} catch (Exception e) {
			System.out.println("Failed: " + e.getLocalizedMessage());
		}
	}

}
