package in.hundredmph.api.domain.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LoginAttemptRepository extends MongoRepository<LoginAttempt, String> {

    List<LoginAttempt> findByEmailAndCreatedAtAfterOrderByCreatedAtDesc(String email, Instant after);

    void deleteByEmail(String email);
}
