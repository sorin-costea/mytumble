package org.sorincos.mytumble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
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

		router.put("/api/status/loadfollowers").handler(this::loadFollowers);
		router.put("/api/status/loadposts").handler(this::loadPosts);
		router.put("/api/status").handler(ctx -> {
			logger.info("Put some other status??");
			ctx.response().setStatusCode(400).setStatusMessage("What status wanna change? loadFollowers or loadPosts?").end();
			return;
		});
		router.get("/api/status").handler(ctx -> {
			logger.info("Get retrieval status");
			ctx.fail(501); // not implemented yet
			return;
		});

		router.get("/api/test").handler(ctx -> {
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
		});

		router.get("/api/followers").handler(ctx -> {
			logger.info("Get followers");
			vertx.eventBus().send("mytumble.mongo.getfollowers", null, new Handler<AsyncResult<Message<JsonArray>>>() {
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
		});

		router.get("/api/posts").handler(ctx -> {
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
		});

		router.route().handler(StaticHandler.create());

		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}

	private void loadFollowers(RoutingContext ctx) {
		logger.info("Retrieve new followers");
		vertx.eventBus().send("mytumble.tumblr.loadfollowers", null, new Handler<AsyncResult<Message<JsonArray>>>() {
			@Override
			public void handle(AsyncResult<Message<JsonArray>> loaded) {
				if (loaded.failed()) {
					ctx.response().setStatusCode(500).setStatusMessage(loaded.cause().getLocalizedMessage()).end();
					return;
				}
				vertx.eventBus().send("mytumble.mongo.savefollowers", loaded, new Handler<AsyncResult<Message<JsonArray>>>() {
			    @Override
			    public void handle(AsyncResult<Message<JsonArray>> saved) {
				    if (saved.failed()) {
					    ctx.response().setStatusCode(500).setStatusMessage(saved.cause().getLocalizedMessage()).end();
					    return;
				    }
				    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				    ctx.response().end(saved.result().body().encode());
				    return;
			    }
		    });
				ctx.response().setStatusCode(500).setStatusMessage("Why did I get here?!?!?").end();
				return;
			}
		});
	}

	private void loadPosts(RoutingContext ctx) {
		JsonArray params = new JsonArray();
		String howMany = ctx.request().getParam("howmany");
		if (null == howMany) {
			howMany = "10";
		}
		logger.info("Retrieve latest " + howMany + " posts");
		params.add(Integer.valueOf(howMany));
		// instead of the JsonArray trick one could write a custom MessageCodec
		// allowing to send an integer and receive a JsonArray...
		vertx.eventBus().send("mytumble.tumblr.loadposts", params, new Handler<AsyncResult<Message<JsonArray>>>() {
			@Override
			public void handle(AsyncResult<Message<JsonArray>> loaded) {
				if (loaded.failed()) {
					ctx.response().setStatusCode(500).setStatusMessage(loaded.cause().getLocalizedMessage()).end();
					return;
				}
				vertx.eventBus().send("mytumble.mongo.saveposts", loaded, new Handler<AsyncResult<Message<JsonArray>>>() {
			    @Override
			    public void handle(AsyncResult<Message<JsonArray>> saved) {
				    if (saved.failed()) {
					    ctx.response().setStatusCode(500).setStatusMessage(saved.cause().getLocalizedMessage()).end();
					    return;
				    }
				    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				    ctx.response().end(saved.result().body().encode());
				    return;
			    }
		    });
				ctx.response().setStatusCode(500).setStatusMessage("Why did I get here?!?!?").end();
				return;
			}
		});

	}
}
