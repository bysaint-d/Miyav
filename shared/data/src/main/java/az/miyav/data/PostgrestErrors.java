package az.miyav.data;

import az.miyav.common.error.AccessDeniedException;
import az.miyav.common.error.PersistenceException;
import az.miyav.common.error.ValidationException;
import az.miyav.data.http.HttpExchange;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class PostgrestErrors {
    private PostgrestErrors() {
    }

    public static RuntimeException from(HttpExchange response) {
        String message = "";
        String code = "";
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("message") && json.get("message").isJsonPrimitive()) {
                message = json.get("message").getAsString();
            }
            if (json.has("code") && json.get("code").isJsonPrimitive()) {
                code = json.get("code").getAsString();
            }
        } catch (RuntimeException ignored) {
            message = "";
        }
        if (response.status() == 401 || "auth.failed".equals(message)) {
            return new PersistenceException("auth.failed");
        }
        if ("FORBIDDEN".equals(message)) {
            return new AccessDeniedException("access.owner");
        }
        if ("23505".equals(code) && message.contains("one_pending")) {
            return new ValidationException("adoption.duplicate");
        }
        if ("CONFLICT".equals(message) || "23505".equals(code)) {
            return new ValidationException("adoption.already_accepted");
        }
        if ("NOT_FOUND".equals(message)) {
            return new ValidationException("adoption.not_pending");
        }
        if ("OUT_OF_AREA".equals(message)) {
            return new ValidationException("location.out_of_area");
        }
        if ("NO_SESSION".equals(message)) {
            return new ValidationException("walk.no_session");
        }
        if ("WALK_TIME".equals(message)) {
            return new ValidationException("validation.walk_time");
        }
        if ("WALK_LISTING_CLOSED".equals(message)) {
            return new ValidationException("walk.listing_closed");
        }
        if ("WALK_OFFER_CLOSED".equals(message)) {
            return new ValidationException("walk.offer_closed");
        }
        if ("BROADCAST_TEXT".equals(message)) {
            return new ValidationException("broadcast.text");
        }
        if ("BROADCAST_RATE".equals(message)) {
            return new ValidationException("broadcast.rate");
        }
        if ("BROADCAST_AUDIENCE".equals(message)) {
            return new ValidationException("validation.city");
        }
        if ("WALK_PET_REQUIRED".equals(message)) {
            return new ValidationException("walk.pet_required");
        }
        if ("WALK_PET_LIMIT".equals(message)) {
            return new ValidationException("walk.pet_limit");
        }
        if ("WALK_OWN_LISTING".equals(message)) {
            return new ValidationException("walk.own_listing");
        }
        if ("WALK_PRICE".equals(message)) {
            return new ValidationException("validation.walk_price");
        }
        if ("WALK_NOTES".equals(message)) {
            return new ValidationException("validation.walk_notes");
        }
        if ("ACCOUNT_SUSPENDED".equals(message)) {
            return new AccessDeniedException("chat.unavailable");
        }
        if ("too many messages".equals(message)) {
            return new ValidationException("chat.rate_limited");
        }
        if ("23514".equals(code) || "22001".equals(code)) {
            return new ValidationException("validation.pet");
        }
        return new PersistenceException("network.unavailable");
    }
}
