package in.hundredmph.api.domain.checkin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface CheckInRepository extends MongoRepository<CheckIn, String> {

    Optional<CheckIn> findByUserIdAndLocalDate(String userId, LocalDate localDate);

    /** Inclusive of both ends — see the note in SessionLogRepository. */
    @Query(value = "{ 'user_id': ?0, 'local_date': { '$gte': ?1, '$lte': ?2 } }",
            sort = "{ 'local_date': 1 }")
    List<CheckIn> findInRange(String userId, LocalDate from, LocalDate to);

    List<CheckIn> findByUserIdOrderByLocalDateDesc(String userId);

    void deleteByUserId(String userId);
}
