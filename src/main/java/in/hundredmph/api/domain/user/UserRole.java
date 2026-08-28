package in.hundredmph.api.domain.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Mirrors UserRole in src/data/types.ts. */
public enum UserRole {
    MEMBER,
    ADMIN;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static UserRole from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
