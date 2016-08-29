package org.sorincos.mytumble;

import static io.vertx.ext.sync.Sync.awaitResult;
import static io.vertx.ext.sync.Sync.fiberHandler;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import co.paralleluniverse.fibers.Suspendable;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

@Component
@ConfigurationProperties(prefix = "web")
public class WebServer extends SyncVerticle {

	static final Logger logger = LoggerFactory.getLogger(WebServer.class);
	private final DeliveryOptions options = new DeliveryOptions().setSendTimeout(5 * 60 * 1000); // 5 minutes, just
	                                                                                             // because

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());

		// trigger actions
		router.put("/api/status/refreshfollowers").handler(fiberHandler(this::refreshFollowers));
		router.put("/api/status/likelikers").handler(fiberHandler(this::likeLikers));
		router.put("/api/status/unfollowasocials").handler(fiberHandler(this::unfollowAsocials));
		router.put("/api/status/refreshposts").handler(fiberHandler(this::refreshPosts));

		// modifying stuff
		router.put("/api/users/:name").handler(fiberHandler(this::updateUser));

		// getting cached data
		router.get("/api/users").handler(this::getUsers);
		router.get("/api/posts").handler(this::getPosts);

		// error conditions
		router.put("/api/status").handler(ctx -> {
			logger.info("Triggering what??");
			ctx.response().setStatusCode(400).setStatusMessage("What do you wanna trigger?").end();
			return;
		});
		router.get("/api/status").handler(ctx -> {
			logger.info("Get retrieval status");
			ctx.fail(501); // not implemented yet
			return;
		});

		router.route().handler(StaticHandler.create());

		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}

	@Suspendable
	private void unfollowAsocials(RoutingContext ctx) {
		logger.info("Unfollowing those who don't follow back");

		vertx.eventBus().send("mytumble.mongo.getusers", createFilter("notspecial,notfollowsme,ifollow,notweird"), options,
		    fiberHandler(result -> {
			    if (result.failed()) {
				    ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
				    return;
			    }
			    ArrayList<Object> notFollowers = Lists.newArrayList((JsonArray) result.result().body());
			    JsonArray notAFollowers = new JsonArray();
			    for (Object notFollower : notFollowers) {
				    @SuppressWarnings("unused")
				    Message<String> res = awaitResult(h -> vertx.eventBus().send("mytumble.tumblr.unfollowblog",
				        ((JsonObject) notFollower).getString("name"), h)); // unfollow them

				    ((JsonObject) notFollower).put("ifollow", false); // and mark for db update
				    notAFollowers.add((JsonObject) notFollower);
			    }
			    @SuppressWarnings("unused")
			    Message<JsonArray> save = awaitResult(
			        h -> vertx.eventBus().send("mytumble.mongo.saveusers", notAFollowers, h)); // remember
			    ctx.response().setStatusCode(200).end();
			    return;
		    }));
	}

	@Suspendable
	private void likeLikers(RoutingContext ctx) {
		logger.info("Liking my likers");

		vertx.eventBus().send("mytumble.mongo.getusers", createFilter("likers"), options, fiberHandler(result -> {
			if (result.failed()) {
				ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
				return;
			}
			ArrayList<Object> likers = Lists.newArrayList((JsonArray) result.result().body());
			for (Object liker : likers) {
				@SuppressWarnings("unused")
				Message<String> res = awaitResult(
				    h -> vertx.eventBus().send("mytumble.tumblr.likelatest", ((JsonObject) liker).getString("name"), h));
			}
			ctx.response().setStatusCode(200).end();
			return;
		}));
	}

	private void getUsers(RoutingContext ctx) {
		String filter = ctx.request().getParam("filter");
		logger.info("Get " + ((filter.length() == 0) ? "" : filter) + " users");

		vertx.eventBus().send("mytumble.mongo.getusers", createFilter(filter), options, result -> {
			if (result.failed()) {
				ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
				return;
			}
			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			ctx.response().end(((JsonArray) result.result().body()).encode());
			return;
		});
	}

	@Deprecated
	private void getPosts(RoutingContext ctx) {
		logger.info("Get posts");
		vertx.eventBus().send("mytumble.mongo.getposts", null, result -> {
			if (result.failed()) {
				ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
				return;
			}
			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			ctx.response().end(((JsonArray) result.result().body()).encode());
			return;
		});
	}

	@Suspendable
	private void refreshFollowers(RoutingContext ctx) {
		logger.info("Refresh followers");

		try {
			Message<JsonArray> loaded = awaitResult(
			    h -> vertx.eventBus().send("mytumble.tumblr.loadfollowers", null, options, h));
			@SuppressWarnings("unused")
			Message<JsonArray> saved = awaitResult(h -> vertx.eventBus().send("mytumble.mongo.saveusers", loaded.body(), h));
			ctx.response().setStatusCode(200).end();
			return;
		} catch (Exception ex) {
			ex.printStackTrace();
			ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			return;
		}
	}

	@Deprecated
	@Suspendable
	private void refreshPosts(RoutingContext ctx) {
		JsonArray params = new JsonArray();
		String howMany = ctx.request().getParam("howmany");
		if (null == howMany) {
			howMany = "10";
		}
		logger.info("Refresh latest " + ((null == howMany) ? "" : Integer.valueOf(howMany)) + " posts");
		params.add(Integer.valueOf(howMany));
		try {
			Message<JsonArray> loaded = awaitResult(h -> vertx.eventBus().send("mytumble.tumblr.loadposts", params, h));
			@SuppressWarnings("unused")
			Message<JsonArray> saved = awaitResult(h -> vertx.eventBus().send("mytumble.mongo.saveposts", loaded.body(), h));
			ctx.response().setStatusCode(200).end();
		} catch (Exception ex) {
			ex.printStackTrace();
			ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			return;
		}
	}

	private void updateUser(RoutingContext ctx) {
		String toUpdate = ctx.request().getParam("name");
		JsonArray jsonUsers = new JsonArray().add(ctx.getBodyAsJson());
		if (null == toUpdate || jsonUsers.isEmpty()) {
			ctx.response().setStatusCode(400).setStatusMessage("Nothing to update.").end();
			return;
		}
		logger.info("Updating " + toUpdate);

		try {
			vertx.eventBus().send("mytumble.mongo.getuser", new JsonArray().add(toUpdate), user -> {
				if (user.succeeded()) {
					Boolean ifollow = ((JsonArray) user.result().body()).getJsonObject(0).getBoolean("ifollow");
					if (ifollow && !jsonUsers.getJsonObject(0).getBoolean("ifollow")) {
						vertx.eventBus().send("mytumble.tumblr.unfollowblog", toUpdate, h -> {
							if (h.failed()) {
								logger.info("Failed when unfollowing " + toUpdate + ": " + h.cause().getLocalizedMessage());
							}
						});
					} else if (!ifollow && jsonUsers.getJsonObject(0).getBoolean("ifollow")) {
						vertx.eventBus().send("mytumble.tumblr.followblog", toUpdate, h -> {
							if (h.failed()) {
								logger.info("Failed when following " + toUpdate + ": " + h.cause().getLocalizedMessage());
							}
						});
					}
				}
			});
			vertx.eventBus().send("mytumble.mongo.saveusers", jsonUsers, h -> {
				if (h.failed()) {
					logger.info("Failed when updating " + toUpdate + ": " + h.cause().getLocalizedMessage());
				}
			});
			ctx.response().setStatusCode(200).end();
			return;
		} catch (Exception ex) {
			ex.printStackTrace();
			ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			return;
		}
	}

	private JsonArray createFilter(String parameters) {
		List<String> filters = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(parameters);
		JsonArray params = new JsonArray();
		for (String filter : filters) {
			params.add(filter);
		}
		return params;
	}
}
