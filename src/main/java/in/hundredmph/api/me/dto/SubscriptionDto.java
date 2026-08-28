package in.hundredmph.api.me.dto;

import in.hundredmph.api.domain.billing.Subscription;
import in.hundredmph.api.domain.billing.SubscriptionStatus;
import java.time.LocalDate;

/** The subscription block of GET /me, per README §5.2. */
public record SubscriptionDto(
        String planId,
        SubscriptionStatus status,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        boolean cancelAtPeriodEnd) {

    public static SubscriptionDto from(Subscription subscription) {
        return new SubscriptionDto(
                subscription.getPlanId(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd());
    }
}
