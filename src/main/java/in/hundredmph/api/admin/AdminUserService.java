package in.hundredmph.api.admin;

import in.hundredmph.api.admin.dto.CreateUserRequest;
import in.hundredmph.api.auth.AuthService;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.common.Phones;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import in.hundredmph.api.me.dto.UserDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

    private static final ZoneId PRACTICE_ZONE = ZoneId.of("Asia/Kolkata");

    private final UserRepository users;
    private final ContentCatalogue catalogue;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public AdminUserService(UserRepository users,
                            ContentCatalogue catalogue,
                            PasswordEncoder passwordEncoder,
                            AuthService authService) {
        this.users = users;
        this.catalogue = catalogue;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public UserDto create(CreateUserRequest request) {
        Instant now = Instant.now();
        String email = AuthService.normaliseEmail(request.email());

        if (users.existsByEmail(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "An account with that email already exists");
        }

        String phone = Phones.normalise(request.phone());
        if (phone != null && !Phones.isPlausible(phone)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "That phone number does not look right");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw ApiException.of(ErrorCode.PHONE_ALREADY_EXISTS,
                    "An account with that phone number already exists");
        }

        authService.assertPasswordStrongEnough(request.password());

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(request.role());
        // Provisioned, not yet used. The first sign-in flips this to active.
        user.setStatus(UserStatus.INVITED);
        user.setActiveProgramId(resolveProgram(request));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTimezone(PRACTICE_ZONE.getId());
        user.setMemberSince(LocalDate.now(PRACTICE_ZONE));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            users.save(user);
        } catch (DuplicateKeyException ex) {
            // Two coaches submitting the same address at once. The unique index
            // is the real arbiter; the check above only makes the common case
            // produce a readable message.
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "An account with that email already exists");
        }

        return UserDto.from(user);
    }

    public List<UserDto> roster() {
        return users.findAll().stream()
                .filter(user -> !user.isDeleted())
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                .map(UserDto::from)
                .toList();
    }

    public UserDto setStatus(String actorId, String userId, UserStatus status) {
        // Suspending yourself would end your own session with nobody left to
        // undo it if you are the only admin.
        if (userId.equals(actorId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "You cannot change the status of your own account");
        }
        User user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));

        Instant now = Instant.now();
        user.setStatus(status);
        user.setUpdatedAt(now);
        users.save(user);

        // Suspension has to bite immediately, so the open sessions go with it.
        if (status == UserStatus.SUSPENDED) {
            authService.revokeAllSessions(userId, now);
        }

        return UserDto.from(user);
    }

    public void setPassword(String userId, String password) {
        authService.setPassword(userId, password);
    }

    private String resolveProgram(CreateUserRequest request) {
        if (request.role() == UserRole.ADMIN) {
            return null;
        }
        // Optional now: the week the coach writes is what a member trains on,
        // and the program is only a focus area for the Learn content.
        if (request.programId() == null || request.programId().isBlank()) {
            return null;
        }
        if (!catalogue.hasProgram(request.programId())) {
            throw ApiException.of(ErrorCode.PROGRAM_NOT_FOUND, "No program with id " + request.programId());
        }
        return request.programId();
    }
}
