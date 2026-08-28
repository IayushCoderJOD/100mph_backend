package in.hundredmph.api.domain.session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SessionLogRepository extends MongoRepository<SessionLog, String> {

    List<SessionLog> findByUserIdOrderByLocalDateDesc(String userId);

    /**
     * A window of days, inclusive of both ends.
     *
     * <p>Written out rather than derived from a method name for two reasons.
     * {@code Between} on MongoDB means {@code $gt}/{@code $lt} — exclusive,
     * unlike the JPA keyword that reads the same — which silently drops the
     * first and last day of every window, today's included. And the obvious
     * fix, {@code ...GreaterThanEqualAnd...LessThanEqual}, cannot be built at
     * all: two criteria on one property collide when the query is assembled.
     */
    @Query(value = "{ 'user_id': ?0, 'local_date': { '$gte': ?1, '$lte': ?2 } }",
            sort = "{ 'local_date': 1 }")
    List<SessionLog> findInRange(String userId, LocalDate from, LocalDate to);

    Optional<SessionLog> findByUserIdAndLocalDate(String userId, LocalDate localDate);

    long countByUserId(String userId);

    void deleteByUserId(String userId);
}
