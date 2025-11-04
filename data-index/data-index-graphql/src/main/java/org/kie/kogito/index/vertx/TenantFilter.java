package org.kie.kogito.index.vertx;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;

import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TenantFilter {

    Logger logger = Logger.getLogger(TenantFilter.class.getName());

    public void add(RoutingContext rc) {

        logger.log(Level.FINE, "[TenantHandler] Handler triggered for path: "+ rc.request().path());

        try {
            JsonObject body = rc.body().asJsonObject();

            if (body == null) {
                rc.next();
                return;
            }

            // Extract tenant ID from JWT
            String tenantId = extractTenantId(rc);

            // Inject tenant ID into GraphQL variables.where
            if (body.containsKey("variables")) {
                JsonObject variables = body.getJsonObject("variables");
                JsonObject where = variables.getJsonObject("where", new JsonObject());
                where.put("tenantId", tenantId);
                variables.put("where", where);
                body.put("variables", variables);

                // Replace the body buffer with modified JSON
                rc.setBody(Buffer.buffer(body.encode()));
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to inject tenant ID: " + e.getMessage(), e);
        }

    }

    private String extractTenantId(RoutingContext rc) {
        if (rc.user() instanceof QuarkusHttpUser quarkusUser) {
            var identity = quarkusUser.getSecurityIdentity();
            var token = identity.getCredential(AccessTokenCredential.class).getToken();
            return parseTenantIdFromJwt(token);
        }
        return "defaultTenant";
    }

    private String parseTenantIdFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length > 1) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonObject json = new JsonObject(payload);
                return json.getString("tenantId", "defaultTenant");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to inject tenant ID: " + e.getMessage(), e);
        }
        return "defaultTenant";
    }
}
