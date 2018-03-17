package org.sorincos.mytumble;

import static io.vertx.ext.sync.Sync.fiberHandler;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import co.paralleluniverse.fibers.Suspendable;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

@Component
@ConfigurationProperties(prefix = "web")
public class WebServer extends SyncVerticle {

    static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private final DeliveryOptions options = new DeliveryOptions().setSendTimeout(5 * 60 * 1000);

    @Override
    public void start() throws Exception {

        Router router = Router.router(vertx);
        BridgeOptions sockJsOpts = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddress("mytumble.web.status"));
        SockJSHandler sockBridgeHandler = SockJSHandler.create(vertx).bridge(sockJsOpts);
        router.route("/eb/*").handler(sockBridgeHandler);

        router.route().handler(BodyHandler.create());

        // trigger actions
        router.put("/api/status/refreshusers").handler(fiberHandler(this::refreshUsers));
        router.put("/api/status/likeusers").handler(fiberHandler(this::likeUsers));
        router.put("/api/status/unfollowasocials").handler(fiberHandler(this::unfollowAsocials));
        router.put("/api/status/followfolks").handler(fiberHandler(this::followFolks));

        // modifying stuff
        router.put("/api/users/:name").handler(fiberHandler(this::updateUser));

        // getting cached data
        router.get("/api/users").handler(this::getUsers);

        router.route().handler(StaticHandler.create());

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private void unfollowAsocials(RoutingContext ctx) {
        logger.info("Unfollowing those who don't follow back");

        try {
            vertx.eventBus().send("mytumble.mongo.getusers", "notspecial,notfollowsme,ifollow", options, result -> {
                ArrayList<Object> notFollowers = Lists.newArrayList((JsonArray) result.result().body());
                loopUnfollowAsocial(notFollowers);
                vertx.eventBus().send("mytumble.web.status", "Unfollowed asocials");
            });
            ctx.response().setStatusCode(200).end();
        } catch (Exception ex) {
            ex.printStackTrace();
            if (ctx.response().ended())
                vertx.eventBus().send("mytumble.web.status",
                        "Unfollowing asocials failed: " + ex.getLocalizedMessage());
            else
                ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
        }
    }

    private void followFolks(RoutingContext ctx) {
        logger.info("Following back not weird followers");

        try {
            vertx.eventBus().send("mytumble.mongo.getusers", "followsme,notweird,notifollow", options, result -> {
                ArrayList<Object> notFolloweds = Lists.newArrayList((JsonArray) result.result().body());
                loopFollowFolks(notFolloweds);
                vertx.eventBus().send("mytumble.web.status", "Followed folks");
            });
            ctx.response().setStatusCode(200).end();
        } catch (Exception ex) {
            ex.printStackTrace();
            if (ctx.response().ended())
                vertx.eventBus().send("mytumble.web.status", "Following folks failed: " + ex.getLocalizedMessage());
            else
                ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
        }
    }

    private void loopFollowFolks(ArrayList<Object> notFolloweds) {
        if (notFolloweds.size() == 0)
            return;
        JsonObject notFollowed = (JsonObject) notFolloweds.get(0);
        vertx.setTimer(1000, t -> {
            vertx.eventBus().send("mytumble.tumblr.followblog", notFollowed.getString("name"), done -> {
                notFollowed.put("ifollow", true);
                vertx.eventBus().send("mytumble.mongo.saveuser", notFollowed, save -> {
                    notFolloweds.remove(0);
                    loopFollowFolks(notFolloweds);
                });
            });
        });
    }

    private void loopUnfollowAsocial(ArrayList<Object> notFollowers) {
        if (notFollowers.size() == 0)
            return;
        JsonObject notFollower = (JsonObject) notFollowers.get(0);
        vertx.setTimer(1000, t -> {
            vertx.eventBus().send("mytumble.tumblr.unfollowblog", notFollower.getString("name"), done -> {
                notFollower.put("ifollow", false);
                vertx.eventBus().send("mytumble.mongo.saveuser", notFollower, save -> {
                    notFollowers.remove(0);
                    loopUnfollowAsocial(notFollowers);
                });
            });
        });
    }

    private void likeUsers(RoutingContext ctx) {
        String filter = ctx.request().getParam("filter");
        String reverse = ctx.request().getParam("reverse");
        try {
            vertx.eventBus().send("mytumble.mongo.getusers", filter, options, result -> {
                logger.info("Liking " + filter + ": " + ((JsonArray) result.result().body()).size());
                List<Object> likers = Lists.newArrayList((JsonArray) result.result().body());
                if (reverse != null)
                    loopLikeUsers(Lists.reverse(likers));
                else
                    loopLikeUsers(likers);
            });
            ctx.response().setStatusCode(200).end();
        } catch (Exception ex) {
            ex.printStackTrace();
            if (ctx.response().ended())
                vertx.eventBus().send("mytumble.web.status", "Liking failed: " + ex.getLocalizedMessage());
            else
                ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
        }
    }

    private void loopLikeUsers(List<Object> likers) {
        if (likers.size() == 0) {
            vertx.eventBus().send("mytumble.web.status", "Liked latest posts");
            return;
        }
        JsonObject liker = (JsonObject) likers.get(0);
        vertx.setTimer(1000, t -> {
            vertx.eventBus().send("mytumble.tumblr.likelatest", liker, done -> {
            });
            likers.remove(0);
            loopLikeUsers(likers);
        });
    }

    private void getUsers(RoutingContext ctx) {
        String filter = ctx.request().getParam("filter");
        logger.info("Get " + ((filter.length() == 0) ? "" : filter) + " users");

        vertx.eventBus().send("mytumble.mongo.getusers", filter, options, result -> {
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
    private void refreshUsers(RoutingContext ctx) {
        logger.info("Refresh followers");

        try {
            vertx.eventBus().send("mytumble.mongo.resetusers", null, resetted -> {
                vertx.eventBus().send("mytumble.tumblr.loadusers", null, options);
            });
            ctx.response().setStatusCode(200).end();
        } catch (Exception ex) {
            ex.printStackTrace();
            if (ctx.response().ended())
                vertx.eventBus().send("mytumble.web.status", "Refresh failed: " + ex.getLocalizedMessage());
            else
                ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
        }
    }

    @Suspendable
    private void cleanupUsers(RoutingContext ctx) {
        logger.info("Cleanup database");

        try {
            vertx.eventBus().send("mytumble.mongo.clearusers", null, resetted -> {
                vertx.eventBus().send("mytumble.tumblr.loadusers", null, options, loaded -> {
                    vertx.eventBus().send("mytumble.mongo.restoreusers", null, options);
                });
            });
            ctx.response().setStatusCode(200).end();
        } catch (Exception ex) {
            ex.printStackTrace();
            if (ctx.response().ended())
                vertx.eventBus().send("mytumble.web.status", "Refresh failed: " + ex.getLocalizedMessage());
            else
                ctx.response().setStatusCode(500).setStatusMessage(ex.getLocalizedMessage()).end();
        }
    }

    private void updateUser(RoutingContext ctx) {
        String toUpdate = ctx.request().getParam("name");
        JsonArray jsonUsers = new JsonArray().add(ctx.getBodyAsJson());
        if (toUpdate == null || jsonUsers.isEmpty()) {
            ctx.response().setStatusCode(400).setStatusMessage("Nothing to update.").end();
            return;
        }
        logger.info("Updating " + toUpdate);
        try {
            vertx.eventBus().send("mytumble.mongo.getuser", new JsonArray().add(toUpdate), resultGet -> {
                if (((JsonArray) resultGet.result().body()).isEmpty()) {
                    vertx.eventBus().send("mytumble.web.status", "Updating failed: " + toUpdate + " not found");
                    return;
                }
                JsonArray userFetched = (JsonArray) resultGet.result().body();
                Boolean iFollow = userFetched.getJsonObject(0).getBoolean("ifollow");
                JsonObject userNew = jsonUsers.getJsonObject(0);
                Boolean shouldFollow = userNew.getBoolean("ifollow");
                if ((iFollow == null || !iFollow) && (shouldFollow != null && shouldFollow)) { // wasnt following
                    vertx.eventBus().send("mytumble.tumblr.followblog", toUpdate, h -> {
                    });
                }
                if ((iFollow != null && iFollow) && (shouldFollow != null && !shouldFollow)) { // previously following
                    vertx.eventBus().send("mytumble.tumblr.unfollowblog", toUpdate, h -> {
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
}
