package in.hundredmph.api.domain.exercise;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExerciseRepository extends MongoRepository<ExerciseDocument, String> {
}
