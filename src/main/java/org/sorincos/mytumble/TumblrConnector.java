package org.sorincos.mytumble;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.Blog;
import com.tumblr.jumblr.types.User;

import io.vertx.core.AbstractVerticle;

@Component
@ConfigurationProperties(prefix = "tumblr")
public class TumblrConnector extends AbstractVerticle {

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

	@Override
	public void start() throws Exception {
		// Create a Tumblr client
		JumblrClient client = new JumblrClient(key, secret);
		client.setToken(oauthtoken, oauthpass);

		// the load function reads in user basic info
		loadUser(client);

	}

	private void loadUser(JumblrClient client) {
		User user = client.user();
		System.out.println(user.getName());

		// And list their blogs
		Blog myBlog = null;
		for (Blog blog : user.getBlogs()) {
			if (0 == blog.getName().compareTo(blogname)) {
				myBlog = blog;
				break;
			}
		}
		if (null == myBlog) {
			System.out.println("Error: Blog name not found!");
			return;
		}
		int numFollowers = myBlog.getFollowersCount();
		System.out.println("Followers: " + numFollowers);

		Map<String, String> options = new HashMap<String, String>();
		options.put("offset", Integer.toString(0));
		List<User> followers = myBlog.followers(options);
		for (Integer offset = 20; offset < numFollowers; offset += 20) {
			System.out.println(offset + "...");
			options.put("offset", Integer.toString(offset));
			followers.addAll(myBlog.followers(options));
		}
		for (User follower : followers) {
			System.out.println(follower.isFollowing() + " - " + follower.getName());
		}
	}
}
