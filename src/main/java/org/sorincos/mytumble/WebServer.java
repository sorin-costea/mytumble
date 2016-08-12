package org.sorincos.mytumble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

@Component
@ConfigurationProperties(prefix = "web")
public class WebServer extends AbstractVerticle {

	static final Logger logger = LoggerFactory.getLogger(WebServer.class);

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());

		// trigger refreshing of data
		router.put("/api/status/refreshfollowers").handler(this::refreshFollowers);
		router.put("/api/status/refreshposts").handler(this::refreshPosts);

		// getting cached data
		router.get("/api/followers").handler(this::getFollowers);
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
		vertx.eventBus().send("mytumble.tumblr.test", null, new Handler<AsyncResult<Message<String>>>() {
			@Override
			public void handle(AsyncResult<Message<String>> result) {
				if (result.failed()) {
					ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
					return;
				}
				ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				ctx.response().end(result.result().body());
				return;
			}
		});
	}

	private void getFollowers(RoutingContext ctx) {
		logger.info("Get followers");
		DeliveryOptions options = new DeliveryOptions();
		options.setSendTimeout(5 * 60 * 1000); // 5 minutes, just because
		vertx.eventBus().send("mytumble.mongo.getfollowers", null, options, new Handler<AsyncResult<Message<JsonArray>>>() {
			@Override
			public void handle(AsyncResult<Message<JsonArray>> result) {
				if (result.failed()) {
					ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
					return;
				}
				ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				ctx.response().end(result.result().body().encode());
				return;
			}
		});
	}

	private void getPosts(RoutingContext ctx) {
		logger.info("Get posts");
		vertx.eventBus().send("mytumble.mongo.getposts", null, new Handler<AsyncResult<Message<JsonArray>>>() {
			@Override
			public void handle(AsyncResult<Message<JsonArray>> result) {
				if (result.failed()) {
					ctx.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
					return;
				}
				ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				ctx.response().end(result.result().body().encode());
				return;
			}
		});
	}

	private void refreshFollowers(RoutingContext ctx) {
		JsonArray params = new JsonArray();
		String howMany = ctx.request().getParam("howmany");
		if (null != howMany) {
			params.add(Integer.valueOf(howMany));
		}
		logger.info("Refresh latest " + ((null == howMany) ? "" : Integer.valueOf(howMany)) + " followers");

		DeliveryOptions options = new DeliveryOptions();
		options.setSendTimeout(5 * 60 * 1000); // 5 minutes, just because
		vertx.eventBus().send("mytumble.tumblr.loadfollowers", params, options,
		    new Handler<AsyncResult<Message<JsonArray>>>() {
			    @Override
			    public void handle(AsyncResult<Message<JsonArray>> loaded) {
				    if (loaded.failed()) {
					    ctx.response().setStatusCode(500).setStatusMessage(loaded.cause().getLocalizedMessage()).end();
					    return;
				    }
				    try {
					    vertx.eventBus().send("mytumble.mongo.savefollowers", loaded.result().body(),
					        new Handler<AsyncResult<Message<JsonArray>>>() {
						        @Override
						        public void handle(AsyncResult<Message<JsonArray>> saved) {
							        if (saved.failed()) {
								        ctx.response().setStatusCode(500).setStatusMessage(saved.cause().getLocalizedMessage()).end();
								        return;
							        }
							        ctx.response().setStatusCode(200).end();
							        return;
						        }
					        });
				    } catch (Exception ex) {
					    ex.printStackTrace();
					    ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
					    return;
				    }
			    }
		    });
	}

	private void refreshPosts(RoutingContext ctx) {
		JsonArray params = new JsonArray();
		String howMany = ctx.request().getParam("howmany");
		if (null == howMany) {
			howMany = "10";
		}
		logger.info("Refresh latest " + ((null == howMany) ? "" : Integer.valueOf(howMany)) + " posts");
		params.add(Integer.valueOf(howMany));
		vertx.eventBus().send("mytumble.tumblr.loadposts", params, new Handler<AsyncResult<Message<JsonArray>>>() {
			@Override
			public void handle(AsyncResult<Message<JsonArray>> loaded) {
				if (loaded.failed()) {
					ctx.response().setStatusCode(500).setStatusMessage(loaded.cause().getLocalizedMessage()).end();
					return;
				}
				try {
					vertx.eventBus().send("mytumble.mongo.saveposts", loaded.result().body(),
					    new Handler<AsyncResult<Message<JsonArray>>>() {
						    @Override
						    public void handle(AsyncResult<Message<JsonArray>> saved) {
							    if (saved.failed()) {
								    ctx.response().setStatusCode(500).setStatusMessage(saved.cause().getLocalizedMessage()).end();
								    return;
							    }
							    ctx.response().setStatusCode(200).end();
							    return;
						    }
					    });
				} catch (Exception ex) {
					ex.printStackTrace();
					ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
					return;
				}
			}
		});
	}
}
