package in.hundredmph.api.account;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.domain.assignment.AssignedExercise;
import in.hundredmph.api.domain.auth.LoginAttempt;
import in.hundredmph.api.domain.auth.PasswordReset;
import in.hundredmph.api.domain.auth.RefreshToken;
import in.hundredmph.api.domain.billing.Subscription;
import in.hundredmph.api.domain.checkin.CheckIn;
import in.hundredmph.api.domain.plan.WeeklyPlan;
import in.hundredmph.api.domain.progression.UserProgression;
import in.hundredmph.api.domain.session.SessionLog;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Removes a person and everything recorded about them — from the database, not
 * behind a flag. A member can ask for it from their own Settings, and an admin
 * can do it for them from the client's page.
 *
 * Pain scores and notes are health data under India's DPDP Act, and the app
 * stores require an in-app way to delete an account, so this is a real delete.
 * Suspending is the reversible option and stays separate.
 *
 * What is left: the person's id on things they wrote for someone else — who
 * last edited a client's week, who prescribed an exercise, who edited a
 * movement. Those belong to the other record, and the readers already fall
 * back when the author no longer exists.
 *
 * MongoDB runs here without transactions, so the user document goes last: if
 * anything fails partway the account still exists, and the same call finishes
 * the job. Every step is safe to repeat.
 */
@Service
public class AccountDeletion {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);

    /** Every collection keyed on the person. Sessions first, so nothing new arrives while the rest goes. */
    private static final List<Class<?>> OWNED_BY_USER = List.of(
            RefreshToken.class,
            PasswordReset.class,
            CheckIn.class,
            SessionLog.class,
            WeeklyPlan.class,
            AssignedExercise.class,
            UserProgression.class,
            Subscription.class);

    private final UserRepository users;
    private final MongoTemplate mongo;

    public AccountDeletion(UserRepository users, MongoTemplate mongo) {
        this.users = users;
        this.mongo = mongo;
    }

    public void delete(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));
        if (user.getRole() == UserRole.ADMIN && !anotherAdminRemains(userId)) {
            throw ApiException.of(ErrorCode.LAST_ADMIN, "This is the practice's only admin account");
        }

        Query owned = Query.query(Criteria.where("userId").is(userId));
        for (Class<?> type : OWNED_BY_USER) {
            mongo.remove(owned, type);
        }
        // Failed sign-ins are kept by email, with the address they came from.
        if (user.getEmail() != null) {
            mongo.remove(Query.query(Criteria.where("email").is(user.getEmail())), LoginAttempt.class);
        }
        users.deleteById(userId);

        // The id only: a name or email here would outlive the account in the logs.
        log.info("Deleted account {} ({})", userId, user.getRole());
    }

    /** Someone else who can still sign in and run the practice. An invited admin can. */
    private boolean anotherAdminRemains(String userId) {
        return users.findByRole(UserRole.ADMIN).stream()
                .anyMatch(admin -> !admin.getId().equals(userId)
                        && !admin.isDeleted()
                        && admin.getStatus() != UserStatus.SUSPENDED);
    }
}
