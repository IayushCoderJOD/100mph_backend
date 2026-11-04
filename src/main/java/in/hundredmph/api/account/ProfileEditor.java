package in.hundredmph.api.account;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.common.Phones;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Writes someone's profile, for both doors onto it: their own Edit Details form
 * and the coach's. The form is saved whole, so a null or blank optional field
 * clears it; that is how a date of birth or a weight comes back out.
 */
@Service
public class ProfileEditor {

    private final UserRepository users;

    public ProfileEditor(UserRepository users) {
        this.users = users;
    }

    public void apply(User user, String fullName, String phone,
                      LocalDate dateOfBirth, Integer heightCm, Double weightKg) {
        String name = fullName.trim();
        // The request is validated before the trim, so " a " gets this far.
        if (name.length() < 2) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "A name needs at least 2 characters");
        }

        String normalised = Phones.normalise(phone);
        if (normalised != null && !Phones.isPlausible(normalised)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "That phone number does not look right");
        }
        if (normalised != null && !normalised.equals(user.getPhone()) && users.existsByPhone(normalised)) {
            throw phoneTaken();
        }

        user.setFullName(name);
        user.setPhone(normalised);
        user.setDateOfBirth(dateOfBirth);
        user.setHeightCm(heightCm);
        user.setWeightKg(weightKg);
        user.setUpdatedAt(Instant.now());
    }

    public void save(User user) {
        try {
            users.save(user);
        } catch (DuplicateKeyException ex) {
            // Two accounts claiming the same detail at the same moment. The
            // unique index settles it, and its name says which detail it was.
            if (String.valueOf(ex.getMessage()).contains("index: email")) {
                throw emailTaken();
            }
            throw phoneTaken();
        }
    }

    public static ApiException emailTaken() {
        return ApiException.of(ErrorCode.EMAIL_ALREADY_EXISTS, "An account with that email already exists");
    }

    private static ApiException phoneTaken() {
        return ApiException.of(ErrorCode.PHONE_ALREADY_EXISTS, "An account with that phone number already exists");
    }
}
