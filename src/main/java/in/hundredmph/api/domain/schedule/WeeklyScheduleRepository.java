package in.hundredmph.api.domain.schedule;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WeeklyScheduleRepository extends MongoRepository<WeeklySchedule, String> {

    Optional<WeeklySchedule> findByUserIdAndProgramId(String userId, String programId);

    void deleteByUserId(String userId);
}
