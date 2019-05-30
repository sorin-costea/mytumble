package org.sorincos.mytumble;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Collectors;

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
    private String timeoutSeconds;

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

    public void setTimeoutSeconds(String timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void start() throws Exception {
        if (vertx.getOrCreateContext().get("jumblrclient") == null) {
            JumblrClient client = new JumblrClient(key, secret);
            client.setToken(oauthtoken, oauthpass);
            client.getRequestBuilder().setTimeoutSeconds(Integer.valueOf(timeoutSeconds));
            vertx.getOrCreateContext().put("jumblrclient", client);
        }

        EventBus eb = vertx.eventBus();
        eb.<String>consumer("mytumble.tumblr.loadusers").handler(this::loadUsers);
        eb.<JsonArray>consumer("mytumble.tumblr.loaduserdetails").handler(this::loadUserDetails);
        eb.<JsonArray>consumer("mytumble.tumblr.readuserlist").handler(this::readUserList);
        eb.<JsonObject>consumer("mytumble.tumblr.likelatest").handler(this::likeLatest);
        eb.<JsonObject>consumer("mytumble.tumblr.lastpostsreacts").handler(this::lastPostsReacts);
        eb.<String>consumer("mytumble.tumblr.followblog").handler(this::followBlog);
        eb.<String>consumer("mytumble.tumblr.unfollowblog").handler(this::unfollowBlog);
    }

    private void lastPostsReacts(Message<JsonObject> msg) {
        Integer howMany = Integer.valueOf(msg.body().getString("posts"));
        try {
            JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
            if (client == null) {
                msg.fail(1, "Error: Jumblr not initialized");
                return;
            }
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("notes_info", "true");
            List<Post> posts = client.blogPosts(blogname, params);
            List<JsonObject> users = new ArrayList<>();
            for (int i = 0; i < howMany; i++) {
                Post post = posts.get(i);
                List<Note> notes = post.getNotes();
                users.addAll(notes.stream().map(note -> {
                    JsonObject user = new JsonObject();
                    user.put("name", note.getBlogName());
                    user.put("avatarurl", client.blogAvatar(note.getBlogName()));
                    return user;
                }).collect(Collectors.toList()));
            }
            logger.info("For latest " + howMany + " posts got: " + users.size() + " users reacting");
            JsonArray jsonUsers = new JsonArray();
            users.forEach(user -> jsonUsers.add(user));
            msg.reply(jsonUsers);
        } catch (Exception ex) {
            ex.printStackTrace();
            msg.fail(1, "ERROR: Loading likers for latest " + howMany + ": " + ex.getLocalizedMessage());
        }
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
            params.put("limit", "3");
            List<Post> posts = client.blogPosts(toLike, params);
            boolean liked = false;
            for (Post post : posts) { // no use to like reblogs or what already liked
                if ((post.getSourceUrl() == null || post.getSourceUrl() == "") && !post.isLiked()) {
                    client.like(post.getId(), post.getReblogKey());
                    logger.info("Original for " + toLike + ": " + post.getShortUrl() + "/" + post.getSourceTitle());
                    liked = true;
                    break;
                }
            }
            if (!liked) {
                for (Post post : posts) { // like the first reblog but not mine
                    if (!post.isLiked() && post.getSourceUrl() != null && post.getSourceUrl() != ""
                            && post.getSourceTitle().compareToIgnoreCase(blogname) != 0) {
                        client.like(post.getId(), post.getReblogKey());
                        logger.info("Liked for " + toLike + ": " + post.getShortUrl() + "/" + post.getSourceTitle());
                        liked = true;
                        break;
                    }
                }
                if (!liked) {
                    logger.info("Nothing to like among latest " + posts.size() + ": " + toLike);
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
            JsonArray jsonEmptyFollowers = new JsonArray();
            jsonFollowers.forEach(jsonFollower -> {
                if (((JsonObject) jsonFollower).getString("avatarurl", "") == "") {
                    jsonEmptyFollowers.add(jsonFollower);
                }
            });
            loopLoadUserDetails(jsonEmptyFollowers, client);
        } catch (Exception ex) {
            ex.printStackTrace();
            vertx.eventBus().send("mytumble.web.status", "Fetching details failed: " + ex.getLocalizedMessage());
            msg.fail(1, ex.getLocalizedMessage());
        }
    }

    private void loopLoadUserDetails(JsonArray jsonFollowers, JumblrClient client) {
        if (jsonFollowers.size() == 0) {
            logger.info("Done loading details.");
            vertx.eventBus().send("mytumble.web.status", "Fetched details from Tumblr");
            return;
        }
        vertx.setTimer(1000, t -> {
            JsonObject jsonFollower = jsonFollowers.getJsonObject(0);
            logger.info("Still " + jsonFollowers.size() + ", fetching: " + jsonFollower.getString("name"));
            try {
                Blog info = client.blogInfo(jsonFollower.getString("name"));
                jsonFollower.put("avatarurl", info.avatar());
                jsonFollower.put("counts", info.getPostCount());
                SimpleDateFormat tumblrDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'GMT'");
                OptionalLong latest = info.posts().stream().mapToLong(post -> {
                    try {
                        return tumblrDate.parse(post.getDateGMT()).getTime();
                    } catch (ParseException e) {
                        logger.error("Error parsing dateGMT for: " + jsonFollower.getString("name") + ": "
                                + post.getDateGMT());
                        return 0;
                    }
                }).max();
                jsonFollower.put("latest", latest.orElse(0));
            } catch (Exception e) {
                logger.warn("Getting info for " + jsonFollower.getString("name") + ": " + e.getLocalizedMessage());
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
                blogs = client.userFollowing(options); // blogs I follow
            } catch (Exception e) {
                logger.info("Error loading blogs I follow: " + e.getLocalizedMessage());
                try {
                    blogs = client.userFollowing(options);
                } catch (Exception e1) {
                    logger.info("Error retrying loading blogs I follow: " + e.getLocalizedMessage());
                    try {
                        blogs = client.userFollowing(options);
                    } catch (Exception e2) {
                        logger.info("Giving up retrying loading blogs I follow: " + e.getLocalizedMessage());
                    }
                }
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
            if (blogs.isEmpty()) { // done with the blogs I follow, time to list my followers
                Blog myBlog = null;
                for (Blog blog : client.user().getBlogs()) {
                    if (blog.getName().compareTo(blogname) == 0) {
                        myBlog = blog; // get the main blog
                        break;
                    }
                }
                if (myBlog == null) {
                    logger.error("Error: Blog name not found - " + blogname);
                    return;
                }
                int numFollowers = myBlog.getFollowersCount();
                logger.info("Following (theoretically): " + mapUsers.size());
                logger.info("Followers (theoretically): " + numFollowers);
                loopLoadFollowers(mapUsers, 0, myBlog); // get the followers in the map
                return;
            }
            loopLoadUsers(mapUsers, offset + 10); // next 10 (small batches)
        });
        return 0;
    }

    private void loopLoadFollowers(Map<String, JsonObject> mapUsers, int offset, Blog myBlog) {
        logger.info("---loop load: " + offset);
        Map<String, String> options = new HashMap<String, String>();
        long now = new Date().getTime();
        vertx.setTimer(1000, t -> {
            options.put("offset", Integer.toString(offset));
            List<User> followers;
            try {
                followers = myBlog.followers(options);
                if (followers.isEmpty()) { // one last chance
                    options.put("offset", Integer.toString(offset + 10));
                    followers = myBlog.followers(options);
                    logger.info("Last chance gave: " + followers.size());
                }
                if (followers.isEmpty()) { // done loading the followers (following are loaded already)
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
            logger.info("---loop loaded: " + offset);
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

    private void readUserList(Message<JsonArray> msg) {
        JsonArray jsonUsers = new JsonArray();
        try {
            JumblrClient client = vertx.getOrCreateContext().get("jumblrclient");
            if (client == null) {
                logger.error("Error: Jumblr not initialized");
                return;
            }
            long now = new Date().getTime();
            Iterator<Object> userIterator = msg.body().iterator();
            vertx.setPeriodic(1000, id -> {
                String user = (String) userIterator.next();
                logger.info("---liker: " + user);
                JsonObject jsonUser = new JsonObject();
                jsonUser.put("_id", user);
                jsonUser.put("name", user);
                jsonUser.put("lastcheck", now); // who cares???
                jsonUser.put("ifollow", false);
                jsonUser.put("followsme", false);
                jsonUser.put("liker", true);
                try {
                    Blog info = client.blogInfo(user);
                    jsonUser.put("avatarurl", info.avatar());
                    jsonUser.put("counts", info.getPostCount());
                    SimpleDateFormat tumblrDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'GMT'");
                    OptionalLong latest = info.posts().stream().mapToLong(post -> {
                        try {
                            return tumblrDate.parse(post.getDateGMT()).getTime();
                        } catch (ParseException e) {
                            logger.error("Error parsing dateGMT for: " + user + ": " + post.getDateGMT());
                            return 0;
                        }
                    }).max();
                    jsonUser.put("latest", latest.orElse(0));
                    jsonUsers.add(jsonUser);
                } catch (Exception e) {
                    logger.error("Error loading info for: " + user + ": " + e.getLocalizedMessage());
                }
                if (!userIterator.hasNext()) {
                    vertx.cancelTimer(id);
                    logger.info("Finished that many likers: " + jsonUsers.size());
                    vertx.eventBus().send("mytumble.web.status", "Finished likers: " + jsonUsers.size());
                    List<Object> sortedUsers = jsonUsers.stream().sorted((o1, o2) -> ((JsonObject) o1).getString("name")
                            .compareTo(((JsonObject) o2).getString("name"))).collect(Collectors.toList());
                    JsonArray jsonSortedUsers = new JsonArray();
                    sortedUsers.forEach(u -> jsonSortedUsers.add(u));
                    msg.reply(jsonSortedUsers);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            msg.fail(1, ex.getLocalizedMessage());
            vertx.eventBus().send("mytumble.web.status", "Refresh failed: " + ex.getLocalizedMessage());
            return;
        }
    }
}
