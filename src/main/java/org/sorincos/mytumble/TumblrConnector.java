package org.sorincos.mytumble;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.Blog;
import com.tumblr.jumblr.types.Note;
import com.tumblr.jumblr.types.Post;
import com.tumblr.jumblr.types.User;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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

		EventBus eb = vertx.eventBus();

		MessageConsumer<JsonArray> loadConsumer = eb.consumer("mytumble.tumblr.loadfollowers");
		loadConsumer.handler(message -> loadFollowers(client));

		MessageConsumer<JsonArray> loadPosts = eb.consumer("mytumble.tumblr.loadposts");
		loadPosts.handler(message -> loadPosts(client));

		loadPosts(client);
	}

	private JsonArray loadPosts(JumblrClient client) {
		Blog myBlog = null;
		for (Blog blog : client.user().getBlogs()) {
			if (0 == blog.getName().compareTo(blogname)) {
				myBlog = blog;
				break;
			}
		}
		if (null == myBlog) {
			System.out.println("Error: Blog name not found - " + blogname);
			return new JsonArray();
		}
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("notes_info", true);
		params.put("reblog_info", true);
		List<Post> posts = myBlog.posts(params);
		JsonArray jsonPosts = new JsonArray();
		for (Post post : posts) {
			JsonObject jsonPost = new JsonObject();
			jsonPost.put("timestamp", post.getTimestamp());
			if (null != post.getRebloggedFromName()) {
				continue; // don't care about what I reblogged
			}
			JsonArray jsonNotes = new JsonArray();
			for (Note note : post.getNotes()) {
				JsonObject jsonNote = new JsonObject();
				jsonNote.put("name", note.getBlogName());
				jsonNote.put("timestamp", note.getTimestamp());
				jsonNote.put("type", note.getType());
				jsonNotes.add(jsonNote);
			}
			jsonPost.put("notes", jsonNotes);
			jsonPosts.add(jsonPost);
		}
		return jsonPosts;
	}

	private JsonArray loadFollowers(JumblrClient client) {
		Blog myBlog = null;
		for (Blog blog : client.user().getBlogs()) {
			if (0 == blog.getName().compareTo(blogname)) {
				myBlog = blog;
				break;
			}
		}
		if (null == myBlog) {
			System.out.println("Error: Blog name not found - " + blogname);
			return new JsonArray();
		}
		int numFollowers = myBlog.getFollowersCount();
		System.out.println("Followers: " + numFollowers);

		Map<String, String> options = new HashMap<String, String>();
		options.put("offset", Integer.toString(0));
		List<User> followers = myBlog.followers(options);
		for (Integer offset = 20; offset < numFollowers; offset += 20) {
			options.put("offset", Integer.toString(offset));
			followers.addAll(myBlog.followers(options));
		}
		JsonArray jsonFollowers = new JsonArray();
		for (User follower : followers) {
			JsonObject jsonFollower = new JsonObject();
			jsonFollower.put("name", follower.getName());
			jsonFollower.put("is_followed", follower.isFollowing());
			String avatar = client.blogAvatar(follower.getName() + ".tumblr.com");
			jsonFollower.put("avatar", avatar);
			jsonFollowers.add(jsonFollower);
		}
		return jsonFollowers;
	}
}
