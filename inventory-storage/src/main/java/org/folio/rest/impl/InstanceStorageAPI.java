package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.resource.InstanceStorageResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceStorageAPI implements InstanceStorageResource {

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final String BLANK_TENANT_MESSAGE = "Tenant Must Be Provided";

  // Replace the replaced IDs
  private static final Map<String, String> replacedToOriginalIdMap
    = new HashMap<>();

  @Override
  public void getInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    Criteria a = new Criteria();
    Criterion criterion = new Criterion(a);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {

        try {
          postgresClient.get("instance", Instance.class, criterion, false,
            reply -> {
              try {
                List<Instance> instances = (List<Instance>) reply.result()[0];

                instances.forEach( item -> {
                  putBackReplacedId(item);
                });

                Instances instanceList = new Instances();
                instanceList.setInstances(instances);
                instanceList.setTotalRecords(instances.size());

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesResponse.
                    withJsonOK(instanceList)));

              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesResponse.
                    withPlainInternalServerError("Error")));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstancesResponse.
              withPlainInternalServerError("Error")));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstancesResponse.
          withPlainInternalServerError("Error")));
    }
  }

  @Override
  public void postInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {

          postgresClient.save("instance", entity,
            reply -> {
              try {
                replacedToOriginalIdMap.put(reply.result(), entity.getId());

                OutStream stream = new OutStream();
                stream.setData(entity);

                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.PostInstanceStorageInstancesResponse
                      .withJsonCreated(reply.result(), stream)));

              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.PostInstanceStorageInstancesResponse
                      .withPlainInternalServerError("Error")));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.PostInstanceStorageInstancesResponse
              .withPlainInternalServerError("Error")));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.PostInstanceStorageInstancesResponse
          .withPlainInternalServerError("Error")));
    }


  }

  @Override
  public void deleteInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    vertxContext.runOnContext(v -> {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      postgresClient.mutate("TRUNCATE TABLE instance",
        reply -> {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.DeleteInstanceStorageInstancesResponse.ok().build()));
        });
    });
  }

  @Override
  public void postInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      PutInstanceStorageInstancesByInstanceIdResponse.withPlainInternalServerError(
        "Not implemented")));
  }

  @Override
  public void getInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    Criteria a = new Criteria();

    a.addField("'id'");
    a.setOperation("=");
    a.setValue(instanceId);

    Criterion criterion = new Criterion(a);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get("instance", Instance.class, criterion, false,
            reply -> {
              try {
                List<Instance> instanceList = (List<Instance>) reply.result()[0];
                if (instanceList.size() == 1) {
                  Instance instance = instanceList.get(0);

                  putBackReplacedId(instance);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                        withJsonOK(instance)));
                } else {
                  throw new Exception(instanceList.size() + " results returned");
                }

              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                    withPlainInternalServerError("Error")));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
              withPlainInternalServerError("Error")));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
          withPlainInternalServerError("Error")));
    }
  }

  @Override
  public void deleteInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      DeleteInstanceStorageInstancesByInstanceIdResponse
        .withPlainInternalServerError("Not implemented")));
  }

  @Override
  public void putInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      PutInstanceStorageInstancesByInstanceIdResponse
        .withPlainInternalServerError("Not implemented")));
  }

  private void putBackReplacedId(Instance instance) {
    if(replacedToOriginalIdMap.containsKey(instance.getId())) {
      instance.setId(replacedToOriginalIdMap.get(instance.getId()));
    }
  }

  private void badRequestResult(
    Handler<AsyncResult<Response>> asyncResultHandler, String message) {
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      InstanceStorageResource.GetInstanceStorageInstancesResponse.withPlainBadRequest(message)));
  }

  private boolean blankTenantId(String tenantId) {
    return tenantId == null || tenantId == "" || tenantId == "folio_shared";
  }
}