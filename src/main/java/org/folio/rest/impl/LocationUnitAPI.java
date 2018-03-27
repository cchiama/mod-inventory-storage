package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Loccamps;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Loclibs;
import org.folio.rest.jaxrs.resource.LocationUnitsResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

// Note - After upgrading to a newer RMB, it should be possible to remove many
// try-catch blocks from the code, which will also improve test coverage, since
// we can not provoke those exceptions...

public class LocationUnitAPI implements LocationUnitsResource {
  private final Messages messages = Messages.getInstance();
  private final Logger logger = LoggerFactory.getLogger(LocationUnitAPI.class);
  public static final String URL_PREFIX = "/location-units";
  public static final String ID_FIELD_NAME = "'id'"; // same for all of them
  public static final String INSTITUTION_TABLE = "locinstitution";
  public static final String URL_PREFIX_INST = URL_PREFIX + "/institutions";
  public static final String INST_SCHEMA_PATH = "apidocs/raml/locinst.json";
  public static final String CAMPUS_TABLE = "loccampus";
  public static final String URL_PREFIX_CAMP = URL_PREFIX + "/campuses";
  public static final String CAMP_SCHEMA_PATH = "apidocs/raml/loccamp.json";
  public static final String LIBRARY_TABLE = "loclibrary";
  public static final String URL_PREFIX_LIB = URL_PREFIX + "/libraries";
  public static final String LIB_SCHEMA_PATH = "apidocs/raml/loclib.json";
  private static final String MOD_NAME = "mod_inventory_storage";

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  private boolean isDuplicate(String message) {
    return message != null
      && message.contains("duplicate key value violates unique constraint");
  }

  private CQLWrapper getCQL(String query, int limit, int offset, String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private String getTenant(Map<String, String> headers) {
    return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
  }

  @Override
  public void deleteLocationUnitsInstitutions(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));
      postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
        tenantId, MOD_NAME, INSTITUTION_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsResponse.noContent()
                .build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsResponse
                .withPlainInternalServerError(reply.cause().getMessage())));
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.DeleteLocationUnitsInstitutionsResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getLocationUnitsInstitutions(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset, INSTITUTION_TABLE);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsInstitutionsResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(          INSTITUTION_TABLE, Locinst.class, new String[]{"*"},
          cql, true, true, reply -> {
            if (reply.failed()) {
              String message = logAndSaveError(reply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsResponse
                  .withPlainBadRequest(message)));
            } else {
              Locinsts insts = new Locinsts();
              List<Locinst> items = (List<Locinst>) reply.result().getResults();
              insts.setLocinsts(items);
              insts.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsResponse.withJsonOK(insts)));
            }
            });
  }

  @Override
  public void postLocationUnitsInstitutions(String lang,
    Locinst entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(INSTITUTION_TABLE, id, entity, reply -> {
      if (reply.failed()) {
          String message = logAndSaveError(reply.cause());
          if (isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                .withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "locinst", entity.getId(),
                    "Institution already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsInstitutionsResponse
                .withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PostLocationUnitsInstitutionsResponse
              .withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationUnitsInstitutionsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    Criterion criterion;
    String tenantId = getTenant(okapiHeaders);
    try {
      Criteria criteria = new Criteria(INST_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
          .withPlainInternalServerError(
            message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(INSTITUTION_TABLE, Locinst.class, criterion, true, false, getReply -> {
          if (getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            List<Locinst> instlist = (List<Locinst>) getReply.result().getResults();
            if (instlist.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                  .withPlainNotFound(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
              // We can safely ignore the case that we have more than one with
              // the same id, RMB has a primary key on ID, will not allow it
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
                  .withJsonOK(instlist.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsInstitutionsById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    Criterion criterion;
    String tenantId = getTenant(okapiHeaders);
    try {
      Criteria criteria = new Criteria(INST_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
          .withPlainInternalServerError(
            message)));
      return;
    }
    instInUse(id, tenantId, vertxContext).setHandler(res -> {
      if (res.failed()) {
        String message = logAndSaveError(res.cause());
        LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
          .withPlainInternalServerError(message);
      } else {
        if (res.result()) {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
              .withPlainBadRequest("Cannot delete institution, as it is in use")));
        } else {
          try {
            PostgresClient.getInstance(vertxContext.owner(), tenantId)
              .delete(INSTITUTION_TABLE, criterion, deleteReply -> {
                if (deleteReply.failed()) {
                  logAndSaveError(deleteReply.cause());
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                      .withPlainNotFound("Institution not found")));
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(
                    LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                      .withNoContent()));
                }
              });
          } catch (Exception e) {
            String message = logAndSaveError(e);
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsInstitutionsByIdResponse
                .withPlainInternalServerError(message)));
          }
        }
      }
    });
  }

  @Override
  public void putLocationUnitsInstitutionsById(
    String id,
    String lang, Locinst entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
          .withPlainBadRequest(message)));
      return;
    }
    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(INST_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(INSTITUTION_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                  .withPlainNotFound("Institution not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsInstitutionsByIdResponse
                  .withNoContent()));
            }
          }
        });
  }

  // TODO - Check that the institution is not used by any location object
  // or by any campus item points here.
  Future<Boolean> instInUse(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    future.complete(false);
    return future;
  }

  ////////////////////////////////////////////
  @Override
  public void deleteLocationUnitsCampuses(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(),
      TenantTool.calculateTenantId(tenantId))
      .mutate(String.format("DELETE FROM %s_%s.%s",
        tenantId, MOD_NAME, CAMPUS_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsCampusesResponse.noContent()
                .build()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.DeleteLocationUnitsCampusesResponse
                .withPlainInternalServerError(reply.cause().getMessage())));
          }
        });
  }

  @Override
  public void getLocationUnitsCampuses(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset, CAMPUS_TABLE);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsCampusesResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, new String[]{"*"},
        cql, true, true, reply -> {
            if (reply.failed()) {
              String message = logAndSaveError(reply.cause());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesResponse
                  .withPlainBadRequest(message)));
            } else {
              Loccamps camps = new Loccamps();
              List<Loccamp> items = (List<Loccamp>) reply.result().getResults();
              camps.setLoccamps(items);
              camps.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesResponse.withJsonOK(camps)));
            }
          });
  }

  @Override
  public void postLocationUnitsCampuses(String lang,
    Loccamp entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(CAMPUS_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = logAndSaveError(reply.cause());
          if (isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsCampusesResponse
                .withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "loccamp", entity.getId(),
                    "Campus already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsCampusesResponse
                .withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PostLocationUnitsCampusesResponse
              .withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationUnitsCampusesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(CAMP_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
          .withPlainInternalServerError(
            message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(CAMPUS_TABLE, Loccamp.class, criterion,
        true, false, getReply -> {
          if (getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            List<Loccamp> items = (List<Loccamp>) getReply.result().getResults();
            if (items.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                  .withPlainNotFound(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else if (items.size() > 1) {
              String message = "Multiple campuses found with the same id";
              logger.error(message);
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                  .withPlainInternalServerError(message)));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsCampusesByIdResponse
                  .withJsonOK(items.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsCampusesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(CAMP_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
          .withPlainInternalServerError(
            message)));
      return;
    }
    campInUse(id, tenantId, vertxContext).setHandler(res -> {
      if (res.failed()) {
        String message = logAndSaveError(res.cause());
        LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
          .withPlainInternalServerError(message);
      } else {
        if (res.result()) {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
              .withPlainBadRequest("Cannot delete campus, as it is in use")));
        } else {
          PostgresClient.getInstance(vertxContext.owner(), tenantId)
            .delete(CAMPUS_TABLE, criterion, deleteReply -> {
              if (deleteReply.failed()) {
                logAndSaveError(deleteReply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
                    .withPlainNotFound("Campus not found")));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.DeleteLocationUnitsCampusesByIdResponse
                    .withNoContent()));
              }
            });
        }
      }
    });
  }

  @Override
  public void putLocationUnitsCampusesById(
    String id,
    String lang, Loccamp entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    if (!id.equals(entity.getId())) {
        String message = "Illegal operation: id cannot be changed";
        asyncResultHandler.handle(Future.succeededFuture(
          LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
            .withPlainBadRequest(message)));
        return;
      }
    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(CAMP_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(CAMPUS_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
                  .withPlainNotFound("Campus not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsCampusesByIdResponse
                  .withNoContent()));
            }
          }
        });
  }

  // TODO - Check that no institution refers to the campus
  Future<Boolean> campInUse(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    future.complete(false);
    return future;
  }

  ////////////////////////////////////////////
  @Override
  public void deleteLocationUnitsLibraries(String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = PostgresClient.getInstance(
      vertxContext.owner(), TenantTool.calculateTenantId(tenantId));
    postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
      tenantId, MOD_NAME, LIBRARY_TABLE),
      reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesResponse.noContent()
              .build()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesResponse
              .withPlainInternalServerError(reply.cause().getMessage())));
        }
      });
  }

  @Override
  public void getLocationUnitsLibraries(
    String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset, LIBRARY_TABLE);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsLibrariesResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(
        LIBRARY_TABLE, Loclib.class, new String[]{"*"},
        cql, true, true, reply -> {
          if (reply.failed()) {
            String message = logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsLibrariesResponse
                .withPlainBadRequest(message)));
          } else {
            Loclibs lib = new Loclibs();
            List<Loclib> items = (List<Loclib>) reply.result().getResults();
            lib.setLoclibs(items);
            lib.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsLibrariesResponse.withJsonOK(lib)));
          }
        });
  }

  @Override
  public void postLocationUnitsLibraries(String lang,
    Loclib entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
      String id = entity.getId();
      if (id == null) {
        id = UUID.randomUUID().toString();
        entity.setId(id);
      }
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .save(LIBRARY_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = logAndSaveError(reply.cause());
          if (isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsLibrariesResponse
                .withJsonUnprocessableEntity(
                  ValidationHelper.createValidationErrorMessage(
                    "loclib", entity.getId(),
                    "Library already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PostLocationUnitsLibrariesResponse
                .withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.PostLocationUnitsLibrariesResponse
              .withJsonCreated(URL_PREFIX + responseObject, stream)));
        }
      });
  }

  @Override
  public void getLocationUnitsLibrariesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(LIB_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
          .withPlainInternalServerError(
            message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LIBRARY_TABLE, Loclib.class, criterion,
        true, false, getReply -> {
          if (getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            List<Loclib> items = (List<Loclib>) getReply.result().getResults();
            if (items.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                  .withPlainNotFound(
                    messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
            } else if (items.size() > 1) {
              String message = "Multiple locations found with the same id";
              logger.error(message);
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                  .withPlainInternalServerError(message)));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.GetLocationUnitsLibrariesByIdResponse
                  .withJsonOK(items.get(0))));
            }
          }
        });
  }

  @Override
  public void deleteLocationUnitsLibrariesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(LIB_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
          .withPlainInternalServerError(
            message)));
      return;
    }
    libInUse(id, tenantId, vertxContext).setHandler(res -> {
      if (res.failed()) {
        String message = logAndSaveError(res.cause());
        LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
          .withPlainInternalServerError(message);
      } else {
        if (res.result()) {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
              .withPlainBadRequest("Cannot delete library, as it is in use")));
        } else {
          PostgresClient.getInstance(vertxContext.owner(), tenantId)
            .delete(LIBRARY_TABLE, criterion, deleteReply -> {
              if (deleteReply.failed()) {
                logAndSaveError(deleteReply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
                    .withPlainNotFound("Library not found")));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  LocationUnitsResource.DeleteLocationUnitsLibrariesByIdResponse
                    .withNoContent()));
              }
            });
        }
      }
    });
  }

  @Override
  public void putLocationUnitsLibrariesById(
    String id,
    String lang, Loclib entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
          .withPlainBadRequest(message)));
      return;
    }
    String tenantId = getTenant(okapiHeaders);
    Criterion criterion;
    try {
      Criteria criteria = new Criteria(LIB_SCHEMA_PATH);
      criteria.addField(ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      criterion = new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
        LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
          .withPlainInternalServerError(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(LIBRARY_TABLE, entity, criterion,
        false, updateReply -> {
          if (updateReply.failed()) {
            String message = logAndSaveError(updateReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
                .withPlainInternalServerError(message)));
          } else {
            if (updateReply.result().getUpdated() == 0) {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
                  .withPlainNotFound("Library not found")));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                LocationUnitsResource.PutLocationUnitsLibrariesByIdResponse
                  .withNoContent()));
            }
          }
        });
  }

  // TODO - Fix this to check something real
  Future<Boolean> libInUse(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    future.complete(false);
    return future;
  }

  /*
  Future<Boolean> instInUseOLDXXXX(String locationId, String tenantId, Context vertxContext) {
    Future<Boolean> future = Future.future();
    //Get all items where the temporary future or permanent future is this location id
    String query = "permanentLocation == " + locationId + " OR temporarylocation == " + locationId;
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCQL(query, 10, 0, ItemStorageAPI.ITEM_TABLE);
        String[] fieldList = {"*"};
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
          ItemStorageAPI.ITEM_TABLE, Item.class, fieldList, cql, true, false,
          getReply -> {
            if (getReply.failed()) {
              future.fail(getReply.cause());
            } else {
              List<Item> itemList = (List<Item>) getReply.result().getResults();
              if (itemList.isEmpty()) {
                future.complete(false);
              } else {
                future.complete(true);
              }
            }
          });
      } catch (Exception e) {
        future.fail(e);
      }
    });
    return future;
  }
*/
}