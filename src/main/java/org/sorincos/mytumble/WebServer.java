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
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

@Component
@ConfigurationProperties(prefix = "web")
public class WebServer extends SyncVerticle {

	static final Logger logger = LoggerFactory.getLogger(WebServer.class);
	private final DeliveryOptions options = new DeliveryOptions().setSendTimeout(5 * 60 * 1000); // 5
																									// minutes,
																									// just
																									// because

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);
		BridgeOptions sockJsOpts = new BridgeOptions()
				.addOutboundPermitted(new PermittedOptions().setAddress("mytumble.web.status"));
		SockJSHandler sockBridgeHandler = SockJSHandler.create(vertx).bridge(sockJsOpts);
		router.route("/eb/*").handler(sockBridgeHandler);

		router.route().handler(BodyHandler.create());

		// trigger actions
		router.put("/api/status/refreshfollowers").handler(fiberHandler(this::refreshFollowers));
		router.put("/api/status/likelikers").handler(fiberHandler(this::likeLikers));
		router.put("/api/status/unfollowasocials").handler(fiberHandler(this::unfollowAsocials));

		// modifying stuff
		router.put("/api/users/:name").handler(fiberHandler(this::updateUser));

		// getting cached data
		router.get("/api/users").handler(this::getUsers);

		router.route().handler(StaticHandler.create());

		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}

	@Suspendable
	private void unfollowAsocials(RoutingContext ctx) {
		logger.info("Unfollowing those who don't follow back");

		try {
			vertx.eventBus().send("mytumble.mongo.getusers", createFilter("notspecial,notfollowsme,ifollow,notweird"),
					options, fiberHandler(result -> {
						ArrayList<Object> notFollowers = Lists.newArrayList((JsonArray) result.result().body());
						JsonArray notAFollowers = new JsonArray();
						for (Object notFollower : notFollowers) {
							@SuppressWarnings("unused")
							Message<String> res = awaitResult(h -> vertx.eventBus().send("mytumble.tumblr.unfollowblog",
									((JsonObject) notFollower).getString("name"), h)); // unfollow
																						// them

							((JsonObject) notFollower).put("ifollow", false); // and
																				// mark
																				// for
																				// db
																				// update
							notAFollowers.add((JsonObject) notFollower);
						}
						vertx.eventBus().send("mytumble.mongo.saveusers", notAFollowers, save -> {
							vertx.eventBus().send("mytumble.web.status", "Unfollowed asocials");
						});
					}));
			ctx.response().setStatusCode(200).end();
		} catch (Exception ex) {
			ex.printStackTrace();
			if (ctx.response().ended()) {
				vertx.eventBus().send("mytumble.web.status",
						"Unfollowing asocials failed: " + ex.getLocalizedMessage());
			} else {
				ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			}
		}
	}

	@Suspendable
	private void likeLikers(RoutingContext ctx) {
		logger.info("Liking my likers");

		try {
			vertx.eventBus().send("mytumble.mongo.getusers", createFilter("likers"), options, fiberHandler(result -> {
				ArrayList<Object> likers = Lists.newArrayList((JsonArray) result.result().body());
				for (Object liker : likers) {
					@SuppressWarnings("unused")
					Message<String> res = awaitResult( // serial for safety
							h -> vertx.eventBus().send("mytumble.tumblr.likelatest",
									((JsonObject) liker).getString("name"), h));
				}
				vertx.eventBus().send("mytumble.web.status", "Liked latest posts");
			}));
			ctx.response().setStatusCode(200).end();
		} catch (Exception ex) {
			ex.printStackTrace();
			if (ctx.response().ended()) {
				vertx.eventBus().send("mytumble.web.status", "Liking failed: " + ex.getLocalizedMessage());
			} else {
				ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			}
		}
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

	@Suspendable
	private void refreshFollowers(RoutingContext ctx) {
		logger.info("Refresh followers");

		try {
			vertx.eventBus().send("mytumble.mongo.resetfollowers", null, resetted -> {
				vertx.eventBus().send("mytumble.tumblr.loadfollowers", null, options);
			});
			ctx.response().setStatusCode(200).end();
		} catch (Exception ex) {
			ex.printStackTrace();
			if (ctx.response().ended()) {
				vertx.eventBus().send("mytumble.web.status", "Refresh failed: " + ex.getLocalizedMessage());
			} else {
				ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			}
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
				if (((JsonArray) user.result().body()).isEmpty()) {
					vertx.eventBus().send("mytumble.web.status", "Updating failed: " + toUpdate + " not found");
					return;
				}
				Boolean ifollow = ((JsonArray) user.result().body()).getJsonObject(0).getBoolean("ifollow");
				if (null == ifollow || (!ifollow && jsonUsers.getJsonObject(0).getBoolean("ifollow"))) {
					vertx.eventBus().send("mytumble.tumblr.followblog", toUpdate, h -> {
						if (h.failed()) {
							logger.info("Failed when following " + toUpdate + ": " + h.cause().getLocalizedMessage());
							vertx.eventBus().send("mytumble.web.status",
									"Failed when following " + toUpdate + ": " + h.cause().getLocalizedMessage());
						}
					});
				} else if (ifollow && !jsonUsers.getJsonObject(0).getBoolean("ifollow")) {
					vertx.eventBus().send("mytumble.tumblr.unfollowblog", toUpdate, h -> {
						if (h.failed()) {
							logger.info("Failed when unfollowing " + toUpdate + ": " + h.cause().getLocalizedMessage());
							vertx.eventBus().send("mytumble.web.status",
									"Failed when unfollowing " + toUpdate + ": " + h.cause().getLocalizedMessage());
						}
					});
				}
				vertx.eventBus().send("mytumble.mongo.saveusers", jsonUsers, h -> {
					if (h.failed()) {
						logger.info("Failed when updating " + toUpdate + ": " + h.cause().getLocalizedMessage());
						vertx.eventBus().send("mytumble.web.status",
								"Failed when updating " + toUpdate + ": " + h.cause().getLocalizedMessage());
					} else {
						vertx.eventBus().send("mytumble.web.status", "Updated user " + toUpdate);
					}
				});
			});
			ctx.response().setStatusCode(200).end();
		} catch (Exception ex) {
			ex.printStackTrace();
			if (ctx.response().ended()) {
				vertx.eventBus().send("mytumble.web.status", "Updating failed: " + ex.getLocalizedMessage());
			} else {
				ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
			}
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
