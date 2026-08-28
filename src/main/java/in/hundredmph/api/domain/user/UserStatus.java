package in.hundredmph.api.domain.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Mirrors UserStatus in src/data/types.ts. */
public enum UserStatus {
    ACTIVE,
    INVITED,
    SUSPENDED;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static UserStatus from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
