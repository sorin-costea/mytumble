package org.sorincos.mytumble;

import java.util.ArrayList;
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
		if (vertx.getOrCreateContext().get("jumblrclient") == null) {
			JumblrClient client = new JumblrClient(key, secret);
			client.setToken(oauthtoken, oauthpass);
			vertx.getOrCreateContext().put("jumblrclient", client);
		}

		EventBus eb = vertx.eventBus();
		eb.<String>consumer("mytumble.tumblr.loadusers").handler(this::loadUsers);
		eb.<JsonArray>consumer("mytumble.tumblr.loaduserdetails").handler(this::loadUserDetails);
		eb.<JsonObject>consumer("mytumble.tumblr.likelatest").handler(this::likeLatest);
		eb.<String>consumer("mytumble.tumblr.followblog").handler(this::followBlog);
		eb.<String>consumer("mytumble.tumblr.unfollowblog").handler(this::unfollowBlog);
	}

	private void likeLatest(Message<JsonObject> msg) {
		String toLike = msg.body().getString("name");
		try {
			JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
			if (client == null) {
				msg.fail(1, "Error: Jumblr not initialized");
				return;
			}

			Map<String, Object> params = new HashMap<String, Object>();
			List<Post> posts = client.blogPosts(toLike, params);
			boolean liked = false;
			for (Post post : posts) { // no use to like reblogs or what already liked
				if (post.getSourceUrl() == null && !post.isLiked()) { // jumblr bug, urls are unusable
					client.like(post.getId(), post.getReblogKey());
					// logger.info(toLike + " liked " + post.getShortUrl());
					liked = true;
					break;
				}
			}
			if (!liked) {
				logger.info("Reblogger or quiet: " + toLike + " had nothing to like among latest " + posts.size());
				for (Post post : posts) { // like the first reblog eh
					if (!post.isLiked()) { // jumblr bug, urls are unusable
						client.like(post.getId(), post.getReblogKey());
						break;
					}
				}
			}
		} catch (Exception ex) {
			msg.fail(1, "ERROR: Liking latest for " + toLike + ": " + ex.getLocalizedMessage());
		}
	}

	private void followBlog(Message<String> msg) {
		String toFollow = msg.body();
		logger.info("Follow " + toFollow);
		try {
			JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
			if (client == null) {
				msg.fail(1, "Error: Jumblr not initialized");
				return;
			}
			client.follow(toFollow);
			msg.reply(toFollow);
		} catch (Exception ex) {
			ex.printStackTrace();
			msg.fail(1, ex.getLocalizedMessage());
		}
	}

	private void unfollowBlog(Message<String> msg) {
		String toUnfollow = msg.body();
		logger.info("Unfollow " + toUnfollow);
		try {
			JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
			if (client == null) {
				msg.fail(1, "Error: Jumblr not initialized");
				return;
			}
			client.unfollow(toUnfollow);
			msg.reply(toUnfollow);
		} catch (Exception ex) {
			ex.printStackTrace();
			msg.fail(1, ex.getLocalizedMessage());
		}
	}

	private void loadUserDetails(Message<JsonArray> msg) {
		try {
			vertx.eventBus().send("mytumble.web.status", "deeetails");
			JsonArray jsonFollowers = msg.body();
			if (jsonFollowers.isEmpty()) {
				msg.fail(1, "No details to load");
				return;
			}
			JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
			if (client == null) {
				msg.fail(1, "Error: Jumblr not initialized");
				return;
			}
			loopLoadUserDetails(jsonFollowers, client);
		} catch (Exception ex) {
			ex.printStackTrace();
			vertx.eventBus().send("mytumble.web.status", "Fetching details failed: " + ex.getLocalizedMessage());
			msg.fail(1, ex.getLocalizedMessage());
		}
	}

	private void loopLoadUserDetails(JsonArray jsonFollowers, JumblrClient client) {
		if (jsonFollowers.size() == 0) {
			vertx.eventBus().send("mytumble.web.status", "Fetched details from Tumblr");
			return;
		}
		vertx.setTimer(1000, t -> {
			JsonObject jsonFollower = (JsonObject) jsonFollowers.getJsonObject(0);
			logger.info("Still " + jsonFollowers.size() + ", fetching: " + jsonFollower.getString("name"));
			try {
				String avatar = client.blogAvatar(jsonFollower.getString("name") + ".tumblr.com");
				jsonFollower.put("avatarurl", avatar);
			} catch (Exception e) {
				logger.warn("Getting avatar for " + jsonFollower.getString("name") + ": " + e.getLocalizedMessage());
			}
			vertx.eventBus().send("mytumble.mongo.saveuser", jsonFollower);
			jsonFollowers.remove(0);
			loopLoadUserDetails(jsonFollowers, client);
		});

	}

	private int loopLoadUsers(Map<String, JsonObject> mapUsers, int offset) {
		JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
		if (client == null) {
			logger.error("Error: Jumblr not initialized");
			return -1;
		}
		Map<String, String> options = new HashMap<String, String>();
		long now = new Date().getTime();
		vertx.setTimer(1000, t -> {
			options.put("offset", Integer.toString(offset));
			List<Blog> blogs = new ArrayList<>();
			try {
				blogs = client.userFollowing(options);
			} catch (Exception e) {
				logger.info("Error loading blogs I follow: " + e.getLocalizedMessage());
			}
			for (Blog blog : blogs) {
				JsonObject jsonIfollow = new JsonObject();
				jsonIfollow.put("_id", blog.getName());
				jsonIfollow.put("name", blog.getName());
				jsonIfollow.put("lastcheck", now); // who cares???
				jsonIfollow.put("ifollow", true);
				jsonIfollow.put("followsme", false);
				mapUsers.put(blog.getName(), jsonIfollow);
				logger.info("ifollow: " + mapUsers.size() + "/" + blog.getName());
			}
			if (blogs.isEmpty()) {
				Blog myBlog = null;
				for (Blog blog : client.user().getBlogs()) {
					if (blog.getName().compareTo(blogname) == 0) {
						myBlog = blog;
						break;
					}
				}
				if (myBlog == null) {
					logger.error("Error: Blog name not found - " + blogname);
					return;
				}
				int numFollowers = myBlog.getFollowersCount();
				logger.info("Followers (theoretically): " + numFollowers);
				loopLoadFollowers(mapUsers, 0, myBlog);
				return;
			}
			loopLoadUsers(mapUsers, offset + 10);
		});
		return 0;
	}

	private void loopLoadFollowers(Map<String, JsonObject> mapUsers, int offset, Blog myBlog) {
		Map<String, String> options = new HashMap<String, String>();
		long now = new Date().getTime();
		vertx.setTimer(1000, t -> {
			options.put("offset", Integer.toString(offset));
			List<User> followers;
			try {
				followers = myBlog.followers(options);
				if (followers.isEmpty()) {
					JsonArray jsonUsers = new JsonArray();
					mapUsers.forEach((k, v) -> jsonUsers.add(v));
					logger.info("Total users: " + jsonUsers.size());
					// TODO logic is all over the program :(
					vertx.eventBus().send("mytumble.mongo.saveusers", jsonUsers, saved -> {
						vertx.eventBus().send("mytumble.web.status", "Refreshed basic info");
						vertx.eventBus().send("mytumble.mongo.getusers", "", loaded -> {
							vertx.eventBus().send("mytumble.tumblr.loaduserdetails", loaded.result().body(),
							        detailed -> {
								        vertx.eventBus().send("mytumble.web.status", "Refreshed also details");
							        });
						});
					});
					return;
				}
			} catch (Exception e) {
				logger.info("Error loading followers at offset " + offset + ": " + e.getLocalizedMessage());
				followers = new ArrayList<>();
			}
			for (User follower : followers) {
				if (mapUsers.get(follower.getName()) != null) {
					mapUsers.get(follower.getName()).put("followsme", true);
					logger.info("followsme: " + mapUsers.size() + "/" + follower.getName());
				} else {
					JsonObject jsonFollower = new JsonObject();
					jsonFollower.put("_id", follower.getName());
					jsonFollower.put("name", follower.getName());
					jsonFollower.put("lastcheck", now);
					jsonFollower.put("followsme", true);
					jsonFollower.put("ifollow", false);
					mapUsers.put(follower.getName(), jsonFollower);
					logger.info("followsme: " + "0/" + follower.getName());
				}
			}
			loopLoadFollowers(mapUsers, offset + followers.size(), myBlog);
		});
	}

	private void loadUsers(Message<String> msg) {
		try {
			vertx.eventBus().send("mytumble.web.status", "loaaaaadin");
			Map<String, JsonObject> mapUsers = new HashMap<>();
			loopLoadUsers(mapUsers, 0);
		} catch (Exception ex) {
			ex.printStackTrace();
			msg.fail(1, ex.getLocalizedMessage());
			vertx.eventBus().send("mytumble.web.status", "Refresh failed: " + ex.getLocalizedMessage());
			return;
		}
		vertx.eventBus().send("mytumble.web.status", "Done refreshing users");
		msg.reply("Done refreshing users");
	}
}
