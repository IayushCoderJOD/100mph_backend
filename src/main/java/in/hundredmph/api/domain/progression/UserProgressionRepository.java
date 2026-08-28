package in.hundredmph.api.domain.progression;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserProgressionRepository extends MongoRepository<UserProgression, String> {

    List<UserProgression> findByUserId(String userId);

    Optional<UserProgression> findByUserIdAndSignatureExerciseId(String userId, String signatureExerciseId);

    void deleteByUserId(String userId);
}
