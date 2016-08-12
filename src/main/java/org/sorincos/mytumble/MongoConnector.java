package org.sorincos.mytumble;

import static io.vertx.ext.sync.Sync.awaitResult;
import static io.vertx.ext.sync.Sync.fiberHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import co.paralleluniverse.fibers.Suspendable;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.sync.SyncVerticle;

@Component
@ConfigurationProperties(prefix = "database")
public class MongoConnector extends SyncVerticle {
	static final Logger logger = LoggerFactory.getLogger(MongoConnector.class);

	private String name;

	public String getName() {
		return name;
	}

	public void setName(String database) {
		this.name = database;
	}

	@Override
	public void start() throws Exception {
		MongoClient mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", name));
		vertx.getOrCreateContext().put("mongoclient", mongo);

		EventBus eb = vertx.eventBus();
		eb.<JsonArray>consumer("mytumble.mongo.savefollowers").handler(this::saveFollowers);
		eb.<JsonArray>consumer("mytumble.mongo.getfollowers").handler(this::getFollowers);
		eb.<JsonArray>consumer("mytumble.mongo.saveposts").handler(this::savePosts);
		eb.<JsonArray>consumer("mytumble.mongo.getposts").handler(this::getPosts);
		eb.<String>consumer("mytumble.mongo.unfollowblog").handler(this::unfollowBlog);
	}

	@Suspendable
	private void saveFollowers(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				msg.body().forEach(follower -> {
					JsonObject upsert = new JsonObject().put("$set", (JsonObject) follower);
					// await, to not kill Mongo's thread pool
					awaitResult(h -> client.updateCollectionWithOptions("followers",
					    new JsonObject().put("name", upsert.getJsonObject("$set").getString("name")), upsert, options, res -> {
						    if (res.failed()) {
							    throw new RuntimeException(res.cause());
						    }
					    }));
				});
				future.complete();
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}), result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	@Suspendable
	private void savePosts(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(fiberHandler(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				msg.body().forEach(post -> {
					JsonObject upsert = new JsonObject().put("$set", (JsonObject) post);
					// await, to not kill Mongo's thread pool with the foreach
					awaitResult(h -> client.updateCollectionWithOptions("posts",
					    new JsonObject().put("postid", upsert.getJsonObject("$set").getLong("postid")), upsert, options, res -> {
						    if (res.failed()) {
							    throw new RuntimeException(res.cause());
						    }
					    }));
				});
				future.complete();
			} catch (Exception ex) {
				ex.printStackTrace();
				future.fail(ex.getLocalizedMessage());
			}
		}), result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void getFollowers(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			MongoClient client = vertx.getOrCreateContext().get("mongoclient");
			client.find("followers", new JsonObject(), res -> {
				if (res.failed()) {
					future.fail(res.cause().getLocalizedMessage());
				}
				final JsonArray followers = new JsonArray();
				res.result().forEach(followers::add);
				future.complete(followers);
			});
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

	private void getPosts(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			MongoClient client = vertx.getOrCreateContext().get("mongoclient");
			@SuppressWarnings("unused")
			Void v = awaitResult(h -> client.createCollection("zzzz", h));
			System.out.println("and");
			client.find("posts", new JsonObject(), res -> {
				if (res.failed()) {
					future.fail(res.cause().getLocalizedMessage());
				}
				final JsonArray posts = new JsonArray();
				res.result().forEach(posts::add);
				future.complete(posts);
			});
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
			String blogName = msg.body();
			MongoClient client = vertx.getOrCreateContext().get("mongoclient");
			client.find("followers", new JsonObject().put("name", blogName), res -> {
				if (res.failed()) {
					future.fail(res.cause().getLocalizedMessage());
				}
				if (1 != res.result().size()) {
					future.fail("Found " + res.result().size() + " results for " + blogName);
				}
				JsonObject follower = res.result().get(0);
				follower.put("special", false);
				follower.put("is_followed", false);
				JsonObject update = new JsonObject().put("$set", (JsonObject) follower);
				UpdateOptions options = new UpdateOptions();
				client.updateCollectionWithOptions("followers",
				    new JsonObject().put("name", update.getJsonObject("$set").getString("name")), update, options, updated -> {
					    if (updated.failed()) {
						    future.fail(updated.cause().getLocalizedMessage());
					    }
				    });
				future.complete();
			});
		}, result -> {
			if (result.succeeded()) {
				msg.reply(result.result());
			} else {
				msg.fail(1, result.cause().getLocalizedMessage());
			}
		});
	}

}
