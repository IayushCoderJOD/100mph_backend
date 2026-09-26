package in.hundredmph.api.me;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.common.Phones;
import in.hundredmph.api.domain.billing.Subscription;
import in.hundredmph.api.domain.billing.SubscriptionRepository;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import in.hundredmph.api.me.dto.EntitlementDto;
import in.hundredmph.api.me.dto.FlagsDto;
import in.hundredmph.api.me.dto.MeResponse;
import in.hundredmph.api.me.dto.SubscriptionDto;
import in.hundredmph.api.me.dto.UpdateMeRequest;
import in.hundredmph.api.me.dto.UserDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MeService {

    /** The Learn tab is still coming-soon in the client; ship it dark. */
    private static final FlagsDto FLAGS = new FlagsDto(false);

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final ContentCatalogue catalogue;

    public MeService(UserRepository users,
                     SubscriptionRepository subscriptions,
                     ContentCatalogue catalogue) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.catalogue = catalogue;
    }

    public MeResponse boot(String userId) {
        User user = requireUser(userId);
        Optional<Subscription> subscription = subscriptions.findFirstByUserIdOrderByCreatedAtDesc(userId);

        return new MeResponse(
                UserDto.from(user),
                subscription.map(SubscriptionDto::from).orElse(null),
                entitlementFor(user, subscription.orElse(null)),
                FLAGS);
    }

    public MeResponse update(String userId, UpdateMeRequest request) {
        User user = requireUser(userId);

        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.timezone() != null) {
            user.setTimezone(parseTimezone(request.timezone()));
        }
        if (request.phone() != null) {
            String phone = Phones.normalise(request.phone());
            if (phone != null && !Phones.isPlausible(phone)) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "That phone number does not look right");
            }
            if (phone != null && !phone.equals(user.getPhone()) && users.existsByPhone(phone)) {
                throw ApiException.of(ErrorCode.PHONE_ALREADY_EXISTS, "An account with that phone number already exists");
            }
            user.setPhone(phone);
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.heightCm() != null) {
            user.setHeightCm(request.heightCm());
        }
        if (request.weightKg() != null) {
            user.setWeightKg(request.weightKg());
        }

        user.setUpdatedAt(Instant.now());
        try {
            users.save(user);
        } catch (DuplicateKeyException ex) {
            // Two accounts claiming the same number at the same moment; the
            // unique index settles it and the loser hears why.
            throw ApiException.of(ErrorCode.PHONE_ALREADY_EXISTS, "An account with that phone number already exists");
        }

        return boot(userId);
    }

    /** PUT /v1/me/program — sets the program the member trains on. */
    public MeResponse setProgram(String userId, String programId) {
        User user = requireUser(userId);

        if (!catalogue.hasProgram(programId)) {
            throw ApiException.of(ErrorCode.PROGRAM_NOT_FOUND, "No program with id " + programId);
        }

        user.setActiveProgramId(programId);
        user.setUpdatedAt(Instant.now());
        users.save(user);

        return boot(userId);
    }

    /**
     * The access decision, computed fresh on every read — never stored, so it
     * cannot go stale, and never sent up by the client, so it cannot be forged.
     *
     * <p>Billing is deliberately not a factor yet: membership is out of scope
     * for this phase, so a member with no subscription still trains. The
     * subscription checks belong here, in this order — account state first, so
     * a suspended member is told they are suspended rather than told to pay —
     * and the hook is left where they go.
     */
    private EntitlementDto entitlementFor(User user, Subscription subscription) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            return EntitlementDto.denied("account_suspended");
        }
        if (user.getRole() == UserRole.ADMIN) {
            return EntitlementDto.staff();
        }

        // A member with no plan yet can still open the app — it shows them
        // their physio has not written their week. Membership gating slots in
        // here once billing exists; until then an active member trains
        // regardless of what the subscription says.
        return EntitlementDto.allowed();
    }

    private User requireUser(String userId) {
        return users.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));
    }

    private static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private static String parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Not a valid IANA timezone: " + timezone);
        }
    }
}
