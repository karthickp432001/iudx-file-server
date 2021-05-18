package iudx.file.server.database.elastic;

import static iudx.file.server.database.utilities.Constants.*;
import java.io.IOException;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ElasticClient {

  private final RestClient client;
  private ResponseBuilder responseBuilder;
  private static final Logger LOGGER = LogManager.getLogger(ElasticClient.class);

  /**
   * ElasticClient - Elastic Low level wrapper.
   * 
   * @param databaseIP IP of the ElasticDB
   * @param databasePort Port of the ElasticDB
   */

  public ElasticClient(String databaseIP, int databasePort, String user, String password) {
    CredentialsProvider credentials = new BasicCredentialsProvider();
    credentials.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
    client = RestClient.builder(new HttpHost(databaseIP, databasePort)).setHttpClientConfigCallback(
        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentials)).build();
  }

  /**
   * searchAsync - Wrapper around elasticsearch async search requests.
   * 
   * @param index Index to search on
   * @param query Query
   * @param searchHandler JsonObject result {@link AsyncResult}
   */
  public ElasticClient searchAsync(String index, String filterPathValue, String query,
      Handler<AsyncResult<JsonObject>> searchHandler) {

    Request queryRequest = new Request("GET", index);
    queryRequest.addParameter(FILTER_PATH, filterPathValue);
    queryRequest.setJsonEntity(query);

    client.performRequestAsync(queryRequest, new ResponseListener() {
      @Override
      public void onSuccess(Response response) {
        JsonArray dbResponse = new JsonArray();
        JsonObject jsonTemp;
        try {
          JsonObject responseJson = new JsonObject(EntityUtils.toString(response.getEntity()));
          if (!responseJson.containsKey(HITS) && !responseJson.containsKey(DOCS_KEY)) {
            responseBuilder =
                new ResponseBuilder(FAILED).setTypeAndTitle(204).setMessage(EMPTY_RESPONSE);
            searchHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
            return;
          }
          responseBuilder = new ResponseBuilder(SUCCESS).setTypeAndTitle(200);
          JsonArray responseHits = new JsonArray();
          if (responseJson.containsKey(HITS)) {
            responseHits = responseJson.getJsonObject(HITS).getJsonArray(HITS);
          } else if (responseJson.containsKey(DOCS_KEY)) {
            responseHits = responseJson.getJsonArray(DOCS_KEY);
          }
          for (Object json : responseHits) {
            jsonTemp = (JsonObject) json;
            dbResponse.add(jsonTemp.getJsonObject(SOURCE_FILTER_KEY));
          }
          responseBuilder.setMessage(dbResponse);
          searchHandler.handle(Future.succeededFuture(responseBuilder.getResponse()));
        } catch (IOException e) {
          LOGGER.error("IO Execption from Database: " + e.getMessage());
          JsonObject ioError = new JsonObject(e.getMessage());
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(ioError);
          searchHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        }
      }

      @Override
      public void onFailure(Exception e) {
        LOGGER.error(e.getLocalizedMessage());
        try {
          String error = e.getMessage().substring(e.getMessage().indexOf("{"),
              e.getMessage().lastIndexOf("}") + 1);
          JsonObject dbError = new JsonObject(error);
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(dbError);
          searchHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        } catch (DecodeException jsonError) {
          LOGGER.error("Json parsing exception: " + jsonError);
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400)
              .setMessage(BAD_PARAMETERS);
          searchHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        }
      }
    });
    return this;
  }


  public ElasticClient insertAsync(String index, JsonObject document,
      Handler<AsyncResult<JsonObject>> insertHandler) {

    StringBuilder putRequestIndex = new StringBuilder(index);
    putRequestIndex.append("/_doc/");
    putRequestIndex.append(UUID.randomUUID().toString());
    Request queryRequest = new Request("PUT", putRequestIndex.toString());
    queryRequest.setJsonEntity(document.toString());

    client.performRequestAsync(queryRequest, new ResponseListener() {

      @Override
      public void onSuccess(Response response) {
        JsonArray dbResponse = new JsonArray();
        JsonObject jsonTemp;
        try {
          JsonObject responseJson = new JsonObject(EntityUtils.toString(response.getEntity()));
          LOGGER.info("response :" + responseJson);
          if (!responseJson.containsKey("result")) {
            responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400)
                .setMessage("Error while inserting.");
            insertHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
          }
          if (responseJson.containsKey("result")
              && responseJson.getString("result").equals("created")) {
            responseBuilder = new ResponseBuilder(SUCCESS).setTypeAndTitle(200);
            insertHandler.handle(Future.succeededFuture(responseBuilder.getResponse()));
          }
        } catch (IOException e) {
          LOGGER.error("IO Execption from Database: " + e.getMessage());
          JsonObject ioError = new JsonObject(e.getMessage());
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(ioError);
          insertHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        }

      }

      @Override
      public void onFailure(Exception ex) {
        try {
          LOGGER.info("error :" + ex.getMessage());
          String error = ex.getMessage().substring(ex.getMessage().indexOf("{"),
              ex.getMessage().lastIndexOf("}") + 1);
          JsonObject dbError = new JsonObject(error);
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(dbError);
          insertHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        } catch (DecodeException jsonError) {
          LOGGER.error("Json parsing exception: " + jsonError);
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400)
              .setMessage(BAD_PARAMETERS);
          insertHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        } catch (Exception e) {
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400)
              .setMessage("DB error");
          insertHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        }
      }
    });

    return this;
  }


  public ElasticClient deleteAsync(String index, String id, String query,
      Handler<AsyncResult<JsonObject>> deletetHandler) {
    StringBuilder deleteURI = new StringBuilder(index);
    deleteURI.append("/_delete_by_query");

    Request deleteRequest = new Request("POST", deleteURI.toString());
    deleteRequest.setJsonEntity(query);

    client.performRequestAsync(deleteRequest, new ResponseListener() {

      @Override
      public void onSuccess(Response response) {
        try {
          JsonObject responseJson = new JsonObject(EntityUtils.toString(response.getEntity()));
          JsonArray failures = responseJson.getJsonArray("failures");
          if (failures != null && failures.size() > 0) {
            responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400)
                .setMessage("Error while deleting.");
            deletetHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
          }
          responseBuilder = new ResponseBuilder(SUCCESS).setTypeAndTitle(200);
          deletetHandler.handle(Future.succeededFuture(responseBuilder.getResponse()));
        } catch (IOException e) {
          LOGGER.error("IO Execption from Database: " + e.getMessage());
          JsonObject ioError = new JsonObject(e.getMessage());
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(ioError);
          deletetHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        }
      }

      @Override
      public void onFailure(Exception ex) {
        try {
          String error = ex.getMessage().substring(ex.getMessage().indexOf("{"),
              ex.getMessage().lastIndexOf("}") + 1);
          JsonObject dbError = new JsonObject(error);
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(dbError);
          deletetHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        } catch (DecodeException jsonError) {
          LOGGER.error("Json parsing exception: " + jsonError);
          responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(400)
              .setMessage(BAD_PARAMETERS);
          deletetHandler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        }
      }
    });
    return this;
  }

}