package org.sorincos.mytumble;

import static io.vertx.ext.sync.Sync.awaitResult;
import static io.vertx.ext.sync.Sync.fiberHandler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.base.Splitter;

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

		// trigger refreshing of data
		router.put("/api/status/refreshfollowers").handler(fiberHandler(this::refreshFollowers));
		router.put("/api/status/refreshposts").handler(fiberHandler(this::refreshPosts));

		// modifying stuff
		router.put("/api/users/:name").handler(fiberHandler(this::updateUser));
		router.post("/api/unfollow").handler(fiberHandler(this::unfollowBlog)); // TODO replace with a simple update

		// getting cached data
		router.get("/api/users").handler(this::getUsers);
		router.get("/api/posts").handler(this::getPosts);
		router.get("/api/test").handler(this::getTest);

		// error conditions
		router.put("/api/status").handler(ctx -> {
			logger.info("Triggering what??");
			ctx.response().setStatusCode(400).setStatusMessage("What do you wanna trigger? loadFollowers or loadPosts?")
			    .end();
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

	private void getTest(RoutingContext ctx) {
		logger.info("Test");
		vertx.eventBus().send("mytumble.tumblr.test", null, result -> {
			if (result.failed()) {
				ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
				return;
			}
			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			ctx.response().end((String) result.result().body());
			return;
		});
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
		JsonArray params = new JsonArray();
		String howMany = ctx.request().getParam("howmany");
		if (null != howMany) {
			params.add(Integer.valueOf(howMany));
		}
		logger.info("Refresh" + ((params.size() == 0) ? "" : (" latest " + Integer.valueOf(howMany))) + " followers");

		try {
			Message<JsonArray> loaded = awaitResult(
			    h -> vertx.eventBus().send("mytumble.tumblr.loadfollowers", params, options, h));
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

	@Deprecated
	@Suspendable
	private void unfollowBlog(RoutingContext ctx) {
		String blogName = ctx.request().getParam("name");
		if (null == blogName) {
			ctx.response().setStatusCode(400).setStatusMessage("Nothing to unfollow.").end();
			return;
		}
		logger.info("Unfollowing " + blogName);

		try {
			@SuppressWarnings("unused") // alternative was casting h to (Consumer<Handler<AsyncResult<Message<String>>>>)...
			Message<String> unfollowed = awaitResult(h -> vertx.eventBus().send("mytumble.tumblr.unfollowblog", blogName, h));

			JsonArray jsonUsers = new JsonArray().add(new JsonObject().put("name", blogName).put("ifollow", false));
			@SuppressWarnings("unused")
			Message<JsonArray> saved = awaitResult(h -> vertx.eventBus().send("mytumble.mongo.saveusers", jsonUsers, h));
			ctx.response().setStatusCode(200).end();
			return;
		} catch (Exception ex) {
			ex.printStackTrace();
			ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			return;
		}
	}

	@Suspendable
	private void updateUser(RoutingContext ctx) {
		String blogName = ctx.request().getParam("name");
		if (null == blogName) {
			ctx.response().setStatusCode(400).setStatusMessage("Nothing to update.").end();
			return;
		}
		logger.info("Updating " + blogName);

		try {
			JsonArray jsonUsers = new JsonArray().add(ctx.getBodyAsJson());
			@SuppressWarnings("unused")
			Message<JsonArray> saved = awaitResult(h -> vertx.eventBus().send("mytumble.mongo.saveusers", jsonUsers, h));
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
