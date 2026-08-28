package in.hundredmph.api.me.dto;

/** GET /v1/me — the boot call. One request, everything the shell needs. */
public record MeResponse(
        UserDto user,
        SubscriptionDto subscription,
        EntitlementDto entitlement,
        FlagsDto flags) {
}
