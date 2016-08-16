package org.sorincos.mytumble;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.Blog;
import com.tumblr.jumblr.types.Note;
import com.tumblr.jumblr.types.Post;
import com.tumblr.jumblr.types.User;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Component
@ConfigurationProperties(prefix = "tumblr")
public class TumblrConnector extends AbstractVerticle {

	static final Logger logger = LoggerFactory.getLogger(TumblrConnector.class);

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
		JumblrClient client = new JumblrClient(key, secret);
		client.setToken(oauthtoken, oauthpass);
		EventBus eb = vertx.eventBus();
		vertx.getOrCreateContext().put("jumblrclient", client);

		eb.<JsonArray>consumer("mytumble.tumblr.loadfollowers").handler(this::loadFollowers);
		eb.<JsonArray>consumer("mytumble.tumblr.loadposts").handler(this::loadPosts);
		eb.<String>consumer("mytumble.tumblr.unfollowblog").handler(this::unfollowBlog);
	}

	private void unfollowBlog(Message<String> msg) {
		vertx.<String>executeBlocking(future -> {
			logger.info("Unfollow " + blogname);
			try {
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				if (null == client) {
					future.fail("Error: Jumblr not initialized");
				}
				Blog myBlog = null;
				for (Blog blog : client.user().getBlogs()) {
					if (0 == blog.getName().compareTo(blogname)) {
						myBlog = blog;
						break;
					}
				}
				if (null == myBlog) {
					future.fail("Error: Blog name not found - " + blogname);
				}
				String toUnfollow = msg.body();
				client.unfollow(toUnfollow); // no return here

				future.complete(toUnfollow);
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void loadPosts(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			logger.info("Posts for " + blogname);
			try {
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				if (null == client) {
					future.fail("Error: Jumblr not initialized");
				}
				Blog myBlog = null;
				for (Blog blog : client.user().getBlogs()) {
					if (0 == blog.getName().compareTo(blogname)) {
						myBlog = blog;
						break;
					}
				}
				if (null == myBlog) {
					future.fail("Error: Blog name not found - " + blogname);
				}
				int howMany = msg.body().getInteger(0);
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("limit", howMany);
				params.put("notes_info", true);
				params.put("reblog_info", true);
				List<Post> posts = myBlog.posts(params);
				JsonArray jsonPosts = new JsonArray();
				int count = 0;
				for (Post post : posts) {
					count++;
					JsonObject jsonPost = new JsonObject();
					jsonPost.put("timestamp", post.getTimestamp());
					jsonPost.put("postid", post.getId());
					if (null != post.getRebloggedFromName()) {
						continue; // don't care about what I reblogged
					}
					logger.info("Post " + count + "/" + posts.size()); // only own posts are logged
					JsonArray jsonNotes = new JsonArray();
					for (Note note : post.getNotes()) {
						logger.info("- " + post.getNoteCount() + "/" + note.getBlogName());
						JsonObject jsonNote = new JsonObject();
						jsonNote.put("name", note.getBlogName());
						jsonNote.put("timestamp", note.getTimestamp());
						jsonNote.put("type", note.getType());
						jsonNotes.add(jsonNote);
					}
					jsonPost.put("notes", jsonNotes);
					jsonPosts.add(jsonPost);
				}
				future.complete(jsonPosts);
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void loadFollowers(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			Blog myBlog = null;
			try {
				long now = new Date().getTime();
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				for (Blog blog : client.user().getBlogs()) {
					if (0 == blog.getName().compareTo(blogname)) {
						myBlog = blog;
						break;
					}
				}
				if (null == myBlog) {
					future.fail("Error: Blog name not found - " + blogname);
				}
				int numFollowers = myBlog.getFollowersCount();
				logger.info("Followers: " + numFollowers);

				Map<String, String> options = new HashMap<String, String>();
				String howMany = msg.body().getString(0);
				if (null != howMany) {
					numFollowers = Integer.parseInt(howMany);
				}
				options.put("offset", Integer.toString(0));
				List<User> followers = myBlog.followers(options);
				for (Integer offset = 20; offset < numFollowers; offset += 20) {
					logger.info(offset + "...");
					options.put("offset", Integer.toString(offset));
					followers.addAll(myBlog.followers(options));
				}
				JsonArray jsonFollowers = new JsonArray();
				int count = 0;
				for (User follower : followers) {
					logger.info("Info about " + ++count + "/" + numFollowers + " " + follower.getName());
					JsonObject jsonFollower = new JsonObject();
					jsonFollower.put("name", follower.getName());
					jsonFollower.put("is_followed", follower.isFollowing());
					String avatar = client.blogAvatar(follower.getName() + ".tumblr.com");
					jsonFollower.put("avatarurl", avatar);
					jsonFollower.put("lastcheck", now);
					jsonFollower.put("special", false);
					jsonFollowers.add(jsonFollower);
				}
				future.complete(jsonFollowers);
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}
}
