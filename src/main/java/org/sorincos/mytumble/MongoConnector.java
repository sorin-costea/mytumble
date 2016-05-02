package org.sorincos.mytumble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

@Component
@ConfigurationProperties(prefix = "database")
public class MongoConnector extends AbstractVerticle {
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
	}

	private void saveFollowers(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				msg.body().forEach(follower -> {
			    JsonObject upsert = new JsonObject().put("$set", (JsonObject) follower);
			    client.updateWithOptions("followers",
		          new JsonObject().put("name", upsert.getJsonObject("$set").getString("name")), upsert, options, res -> {
			          if (res.succeeded()) {
				          future.complete();
			          } else {
				          future.fail(res.cause().getLocalizedMessage());
			          }
		          });
		    });
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

	private void savePosts(Message<JsonArray> msg) {
		vertx.<JsonArray>executeBlocking(future -> {
			try {
				MongoClient client = vertx.getOrCreateContext().get("mongoclient");
				UpdateOptions options = new UpdateOptions().setUpsert(true);
				msg.body().forEach(post -> {
			    JsonObject upsert = new JsonObject().put("$set", (JsonObject) post);
			    client.updateWithOptions("posts",
		          new JsonObject().put("postid", upsert.getJsonObject("$set").getString("postid")), upsert, options,
		          res -> {
			          if (res.succeeded()) {
				          future.complete();
			          } else {
				          future.fail(res.cause().getLocalizedMessage());
			          }
		          });
		    });
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

}
