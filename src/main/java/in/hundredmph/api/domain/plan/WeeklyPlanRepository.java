package in.hundredmph.api.domain.plan;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WeeklyPlanRepository extends MongoRepository<WeeklyPlan, String> {
    Optional<WeeklyPlan> findByUserId(String userId);
}
