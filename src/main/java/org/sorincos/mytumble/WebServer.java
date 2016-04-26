package org.sorincos.mytumble;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

@Component
@ConfigurationProperties(prefix = "web")
public class WebServer extends AbstractVerticle {

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());

		router.get("/api/followers").handler(ctx -> {
			vertx.eventBus().send("mytumble.tumblr.loadfollowers", null, new Handler<AsyncResult<Message<JsonArray>>>() {
			  @Override
			  public void handle(AsyncResult<Message<JsonArray>> result) {
				  if (result.failed()) {
					  ctx.fail(500);
					  return;
				  }
				  // TODO how about saving to DB directly here?
				  ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				  ctx.response().end(result.result().body().encode());
				  return;
			  }
		  });
		});

		router.get("/api/posts").handler(ctx -> {
			vertx.eventBus().send("mytumble.tumblr.loadposts", null, new Handler<AsyncResult<Message<JsonArray>>>() {
			  @Override
			  public void handle(AsyncResult<Message<JsonArray>> result) {
				  if (result.failed()) {
					  ctx.fail(500);
					  return;
				  }
				  // TODO how about saving to DB directly here?
				  ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				  ctx.response().end(result.result().body().encode());
				  return;
			  }
		  });
		});

		router.get("/api/users").handler(ctx -> {
			vertx.eventBus().send("mytumble.mongo.findall", new JsonObject(), new Handler<AsyncResult<Message<JsonArray>>>() {
			  @Override
			  public void handle(AsyncResult<Message<JsonArray>> result) {
				  if (result.failed()) {
					  ctx.fail(500);
					  return;
				  }
				  // now convert the list to a JsonArray because it will be easier
		      // to encode the final object as the response.
				  ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				  ctx.response().end(result.result().body().encode());
				  return;
			  }
		  });
		});

		router.get("/api/users/:id").handler(ctx -> {
			vertx.eventBus().send("mytumble.mongo.findone", new JsonObject().put("_id", ctx.request().getParam("id")),
		      new Handler<AsyncResult<Message<JsonArray>>>() {

			      @Override
			      public void handle(AsyncResult<Message<JsonArray>> result) {
				      if (result.failed()) {
					      ctx.fail(500);
					      return;
				      }
				      if (result.result().body() == null) {
					      ctx.fail(404);
					      return;
				      }
				      ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				      ctx.response().end(result.result().body().encode());
				      return;
			      }
		      });
		});

		router.post("/api/users").handler(ctx -> {
			JsonObject newUser = ctx.getBodyAsJson();
			if (null == newUser) {
				ctx.fail(400);
				return;
			}
			vertx.eventBus().send("mytumble.mongo.findone", new JsonObject().put("username", newUser.getString("username")),
		      new Handler<AsyncResult<Message<JsonArray>>>() {
			      @Override
			      public void handle(AsyncResult<Message<JsonArray>> result) {
				      if (result.failed()) {
					      ctx.fail(500);
					      return;
				      }
				      JsonArray user = result.result().body();
				      if (user != null) {
					      // already exists
					      ctx.fail(500);
					      return;
				      }
				      vertx.eventBus().send("mytumble.mongo.insert", user, new Handler<AsyncResult<Message<JsonArray>>>() {
			          @Override
			          public void handle(AsyncResult<Message<JsonArray>> insert) {
				          if (insert.failed()) {
					          ctx.fail(500);
					          return;
				          }

				          // add the generated id to the user object
				          newUser.put("_id", insert.result());

				          ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				          ctx.response().end(newUser.encode());
				          return;
			          }
		          });
			      }
		      });
		});
		//
		// router.put("/api/users/:id").handler(ctx -> {
		// mongo.findOne("users", new JsonObject().put("_id",
		// ctx.request().getParam("id")), null, lookup -> {
		// // error handling
		// if (lookup.failed()) {
		// ctx.fail(500);
		// return;
		// }
		//
		// JsonObject user = lookup.result();
		//
		// if (user == null) {
		// // does not exist
		// ctx.fail(404);
		// } else {
		//
		// // update the user properties
		// JsonObject update = ctx.getBodyAsJson();
		//
		// user.put("username", update.getString("username"));
		// user.put("firstName", update.getString("firstName"));
		// user.put("lastName", update.getString("lastName"));
		// user.put("address", update.getString("address"));
		//
		// mongo.replace("users", new JsonObject().put("_id",
		// ctx.request().getParam("id")), user, replace -> {
		// // error handling
		// if (replace.failed()) {
		// ctx.fail(500);
		// return;
		// }
		//
		// ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		// ctx.response().end(user.encode());
		// });
		// }
		// });
		// });
		//
		// router.delete("/api/users/:id").handler(ctx -> {
		// mongo.findOne("users", new JsonObject().put("_id",
		// ctx.request().getParam("id")), null, lookup -> {
		// // error handling
		// if (lookup.failed()) {
		// ctx.fail(500);
		// return;
		// }
		//
		// JsonObject user = lookup.result();
		//
		// if (user == null) {
		// // does not exist
		// ctx.fail(404);
		// } else {
		//
		// mongo.remove("users", new JsonObject().put("_id",
		// ctx.request().getParam("id")), remove -> {
		// // error handling
		// if (remove.failed()) {
		// ctx.fail(500);
		// return;
		// }
		//
		// ctx.response().setStatusCode(204);
		// ctx.response().end();
		// });
		// }
		// });
		// });
		//
		// Serve the static pages
		router.route().handler(StaticHandler.create());

		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}

}
