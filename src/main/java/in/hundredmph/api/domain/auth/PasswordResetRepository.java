package in.hundredmph.api.domain.auth;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PasswordResetRepository extends MongoRepository<PasswordReset, String> {
    Optional<PasswordReset> findByTokenHash(String tokenHash);
}
