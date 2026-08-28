package in.hundredmph.api.domain.billing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The client's SubscriptionStatus is the narrow set (active/expired/pending).
 * The extra states exist because a payment provider will produce them whether
 * or not the app models them; they are collapsed for the client in the
 * entitlement object, never by dropping information here.
 */
public enum SubscriptionStatus {
    PENDING,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    EXPIRED;

    /** True when the member should be able to train right now. */
    public boolean grantsAccess() {
        return this == ACTIVE || this == TRIALING;
    }

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static SubscriptionStatus from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
