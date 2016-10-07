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
import com.tumblr.jumblr.exceptions.JumblrException;
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
		if (null == vertx.getOrCreateContext().get("jumblrclient")) {
			JumblrClient client = new JumblrClient(key, secret);
			client.setToken(oauthtoken, oauthpass);
			vertx.getOrCreateContext().put("jumblrclient", client);
		}

		EventBus eb = vertx.eventBus();
		eb.<JsonArray>consumer("mytumble.tumblr.loadusers").handler(this::loadUsers);
		eb.<JsonArray>consumer("mytumble.tumblr.loaduserdetails").handler(this::loadUserDetails);
		eb.<JsonArray>consumer("mytumble.tumblr.loadposts").handler(this::loadPosts);
		eb.<String>consumer("mytumble.tumblr.likelatest").handler(this::likeLatest);
		eb.<String>consumer("mytumble.tumblr.followblog").handler(this::followBlog);
		eb.<String>consumer("mytumble.tumblr.unfollowblog").handler(this::unfollowBlog);
	}

	private void likeLatest(Message<String> msg) {
		vertx.<String>executeBlocking(future -> {
			String toLike = msg.body();
			try {
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				if (null == client) {
					future.fail("Error: Jumblr not initialized");
				}
				Map<String, Object> params = new HashMap<String, Object>();
				List<Post> posts = client.blogPosts(toLike, params);
				for (Post post : posts) {
					if (null == post.getSourceUrl()) { // no use to like reblogs
						client.like(post.getId(), post.getReblogKey());
						logger.info("Liked " + toLike + ":" + post.getId());
						break; // like only latest own post
					}
				}
				future.complete(toLike);
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

	private void followBlog(Message<String> msg) {
		vertx.<String>executeBlocking(future -> {
			String toFollow = msg.body();
			logger.info("Follow " + toFollow);
			try {
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				if (null == client) {
					future.fail("Error: Jumblr not initialized");
				}
				client.follow(toFollow); // no return here
				future.complete(toFollow);
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

	private void unfollowBlog(Message<String> msg) {
		vertx.<String>executeBlocking(future -> {
			String toUnfollow = msg.body();
			logger.info("Unfollow " + toUnfollow);
			try {
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				if (null == client) {
					future.fail("Error: Jumblr not initialized");
				}
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

	@Deprecated
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
					JsonObject jsonPost = new JsonObject();
					jsonPost.put("timestamp", post.getTimestamp());
					jsonPost.put("postid", post.getId());
					if (null != post.getRebloggedFromName()) {
						continue; // don't care about what I reblogged
					}
					count++;
					logger.info("Post " + count);
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

	private void loadUserDetails(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			try {
				JsonArray jsonFollowers = msg.body();
				if (jsonFollowers.isEmpty()) {
					future.fail("No details to load");
				}
				JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
				int count = 0;
				loopUsers(jsonFollowers, client, count);
				future.complete(jsonFollowers);
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}, result -> {
			if (result.succeeded()) {
				vertx.eventBus().send("mytumble.web.status", "Fetched details from Tumblr");
				msg.reply(result.result());
			} else {
				vertx.eventBus().send("mytumble.web.status",
				        "Fetching details failed: " + result.cause().getLocalizedMessage());
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private int loopUsers(JsonArray jsonFollowers, JumblrClient client, final int count) {
		if (jsonFollowers.size() > count) {
			vertx.setTimer(100, t -> {
				JsonObject jsonFollower = (JsonObject) jsonFollowers.getJsonObject(count);
				final int nrCrt = count + 1;
				logger.info("Info about " + nrCrt + "/" + jsonFollowers.size() + " " + jsonFollower.getString("name"));
				try {
					String avatar = client.blogAvatar(jsonFollower.getString("name") + ".tumblr.com");
					jsonFollower.put("avatarurl", avatar);
				} catch (JumblrException e) {
					logger.warn("Getting avatar for" + jsonFollower.getString("name") + ": " + e.getLocalizedMessage());
				}
				vertx.eventBus().send("mytumble.mongo.saveuser", jsonFollower);
				loopUsers(jsonFollowers, client, count + 1);
			});
		} else {
			vertx.eventBus().send("mytumble.web.status", "Finished details from Tumblr");
		}
		return count;
	}

	private void loadUsers(Message<JsonArray> msg) {
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
				Map<String, JsonObject> mapUsers = new HashMap<>();
				Map<String, String> options = new HashMap<String, String>();
				int offset = 0, fetched = 0;
				do {
					options.put("offset", Integer.toString(offset));
					List<Blog> blogs = client.userFollowing(options);
					fetched = blogs.size();
					offset += fetched;
					for (Blog blog : blogs) {
						JsonObject jsonIfollow = new JsonObject();
						jsonIfollow.put("_id", blog.getName());
						jsonIfollow.put("name", blog.getName());
						jsonIfollow.put("lastcheck", now);
						jsonIfollow.put("ifollow", true);
						jsonIfollow.put("followsme", false);
						mapUsers.put(blog.getName(), jsonIfollow);
					}
				} while (fetched > 0);
				logger.info("I follow: " + mapUsers.size());

				int numFollowers = myBlog.getFollowersCount();
				logger.info("Followers (theoretically): " + numFollowers);

				offset = 0;
				do {
					options.put("offset", Integer.toString(offset));
					List<User> followers = myBlog.followers(options);
					fetched = followers.size();
					offset += fetched;
					for (User follower : followers) {
						if (mapUsers.get(follower.getName()) != null) {
							mapUsers.get(follower.getName()).put("followsme", true);
						} else {
							JsonObject jsonFollower = new JsonObject();
							jsonFollower.put("_id", follower.getName());
							jsonFollower.put("name", follower.getName());
							jsonFollower.put("lastcheck", now);
							jsonFollower.put("followsme", true);
							jsonFollower.put("ifollow", false);
							mapUsers.put(follower.getName(), jsonFollower);
						}
					}
				} while (fetched > 0);

				JsonArray jsonUsers = new JsonArray();
				mapUsers.forEach((k, v) -> jsonUsers.add(v));
				logger.info("Total users: " + jsonUsers.size());
				future.complete(jsonUsers);
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}, result ->

		{
			if (result.succeeded()) {
				vertx.eventBus().send("mytumble.mongo.saveusers", result.result(), saved -> {
					vertx.eventBus().send("mytumble.web.status", "Refreshed from Tumblr");
					vertx.eventBus().send("mytumble.mongo.getusers", "", loaded -> {
						vertx.eventBus().send("mytumble.tumblr.loaduserdetails", loaded.result().body());
					});
				});
				msg.reply("Refreshed from Tumblr");
			} else {
				vertx.eventBus().send("mytumble.web.status", "Refresh failed: " + result.cause().getLocalizedMessage());
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}
}
