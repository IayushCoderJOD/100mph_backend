package in.hundredmph.api.me.dto;

/**
 * The only thing the client may branch on for access (README §5.2). Computed on
 * every request from the subscription and the account state — never stored, so
 * it cannot go stale, and never sent up by the client, so it cannot be forged.
 */
public record EntitlementDto(boolean canTrain, boolean canViewLearn, String reason) {

    public static EntitlementDto allowed() {
        return new EntitlementDto(true, true, null);
    }

    public static EntitlementDto denied(String reason) {
        return new EntitlementDto(false, false, reason);
    }

    /** Staff have the back office but no program of their own. */
    public static EntitlementDto staff() {
        return new EntitlementDto(false, true, "staff_account");
    }
}
