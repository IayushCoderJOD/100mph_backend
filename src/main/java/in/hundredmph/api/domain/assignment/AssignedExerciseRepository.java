package in.hundredmph.api.domain.assignment;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssignedExerciseRepository extends MongoRepository<AssignedExercise, String> {

    List<AssignedExercise> findByUserIdAndActiveTrueOrderBySortOrderAsc(String userId);

    List<AssignedExercise> findByUserIdOrderBySortOrderAsc(String userId);

    void deleteByUserId(String userId);
}
