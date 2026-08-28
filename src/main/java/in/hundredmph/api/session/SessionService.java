package in.hundredmph.api.session;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.content.model.SessionExercise;
import in.hundredmph.api.content.model.SessionType;
import in.hundredmph.api.domain.schedule.DayOfWeek;
import in.hundredmph.api.domain.session.SessionLog;
import in.hundredmph.api.domain.session.SessionLogRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.schedule.ScheduleService;
import in.hundredmph.api.session.dto.LogSessionRequest;
import in.hundredmph.api.session.dto.SessionLogResponse;
import in.hundredmph.api.session.dto.SessionPlanResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final SessionLogRepository logs;
    private final UserRepository users;
    private final ContentCatalogue catalogue;
    private final ScheduleService schedules;

    public SessionService(SessionLogRepository logs,
                          UserRepository users,
                          ContentCatalogue catalogue,
                          ScheduleService schedules) {
        this.logs = logs;
        this.users = users;
        this.catalogue = catalogue;
        this.schedules = schedules;
    }

    /**
     * What a given day asks of this member. Reads the weekly template for the
     * day, then expands the session type into its running order.
     */
    public SessionPlanResponse planFor(String userId, LocalDate date) {
        User user = requireUser(userId);
        String programId = requireProgram(user);
        LocalDate day = date != null ? date : today(user);

        String sessionTypeId = schedules.plannedSessionType(userId, programId, DayOfWeek.of(day));
        boolean completed = logs.findByUserIdAndLocalDate(userId, day).isPresent();

        if (sessionTypeId == null) {
            // A rest day is a valid answer, not an error.
            return new SessionPlanResponse(null, List.of(), completed);
        }

        SessionType sessionType = catalogue.sessionType(sessionTypeId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SESSION_TYPE_NOT_FOUND,
                        "No session type with id " + sessionTypeId));

        List<SessionPlanResponse.PlannedExercise> planned = new ArrayList<>();
        for (SessionExercise link : catalogue.sessionExercisesFor(sessionTypeId)) {
            catalogue.exercise(link.exerciseId()).ifPresent(exercise ->
                    planned.add(new SessionPlanResponse.PlannedExercise(exercise, link.prescription())));
        }

        return new SessionPlanResponse(sessionType, planned, completed);
    }

    /**
     * Records a completed session.
     *
     * <p>Safe to call twice with the same id: the second call updates the same
     * document rather than creating another. That is what makes the offline
     * retry path correct instead of duplicating a member's week.
     */
    public SessionLogResponse log(String userId, LogSessionRequest request) {
        User user = requireUser(userId);
        String programId = requireProgram(user);
        Instant now = Instant.now();
        LocalDate day = request.localDate() != null ? request.localDate() : today(user);

        String sessionTypeId = request.sessionTypeId() != null
                ? request.sessionTypeId()
                : schedules.plannedSessionType(userId, programId, DayOfWeek.of(day));

        if (sessionTypeId != null && !catalogue.sessionTypeBelongsTo(sessionTypeId, programId)) {
            throw ApiException.of(ErrorCode.SESSION_TYPE_NOT_FOUND,
                    "session_type_id " + sessionTypeId + " does not belong to program " + programId);
        }

        // A same-day log that came from a different tap replaces the earlier
        // one; the (user, date) index means a day has one session, not a pile.
        SessionLog log = logs.findById(request.id())
                .or(() -> logs.findByUserIdAndLocalDate(userId, day))
                .orElseGet(SessionLog::new);

        if (log.getId() != null && !log.getUserId().equals(userId)) {
            // Someone else's id was supplied. Refuse rather than overwrite.
            throw ApiException.of(ErrorCode.FORBIDDEN, "That session does not belong to this account");
        }

        if (log.getId() == null) {
            log.setId(request.id());
            log.setCreatedAt(now);
        }

        log.setUserId(userId);
        log.setProgramId(programId);
        log.setSessionTypeId(sessionTypeId);
        log.setLocalDate(day);
        log.setSource(request.source() == null ? "logged" : request.source());
        log.setDurationMin(request.durationMin());
        log.setNote(request.note());
        log.setCompletedAt(now);
        log.setCompletedExercises(toCompleted(request.exercises()));

        try {
            logs.save(log);
        } catch (DuplicateKeyException ex) {
            // Two taps racing on the same day. The row that landed first wins.
            return logs.findByUserIdAndLocalDate(userId, day)
                    .map(SessionLogResponse::from)
                    .orElseThrow(() -> ApiException.of(ErrorCode.INTERNAL_ERROR, "Could not save the session"));
        }

        return SessionLogResponse.from(log);
    }

    /** The member's history, optionally bounded — the week strip and progress tab. */
    public List<SessionLogResponse> history(String userId, LocalDate from, LocalDate to) {
        List<SessionLog> found = (from != null && to != null)
                ? logs.findInRange(userId, from, to)
                : logs.findByUserIdOrderByLocalDateDesc(userId);
        return found.stream().map(SessionLogResponse::from).toList();
    }

    /** The dates the member has completed, which is all the week strip needs. */
    public List<LocalDate> completedDates(String userId) {
        return logs.findByUserIdOrderByLocalDateDesc(userId).stream()
                .map(SessionLog::getLocalDate)
                .toList();
    }

    public void delete(String userId, String sessionId) {
        SessionLog log = logs.findById(sessionId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such session"));
        if (!log.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "That session does not belong to this account");
        }
        logs.delete(log);
    }

    // ---------------------------------------------------------------- helpers

    private static List<SessionLog.CompletedExercise> toCompleted(
            List<LogSessionRequest.CompletedExerciseInput> input) {
        if (input == null) return List.of();
        return input.stream()
                .map(e -> new SessionLog.CompletedExercise(
                        e.exerciseId(),
                        e.completed() == null || e.completed(),
                        e.note()))
                .toList();
    }

    private User requireUser(String userId) {
        return users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));
    }

    private static String requireProgram(User user) {
        String programId = user.getActiveProgramId();
        if (programId == null) {
            throw ApiException.of(ErrorCode.NO_ACTIVE_PROGRAM, "This account is not on a program");
        }
        return programId;
    }

    /** Today in the member's own zone — README's local-calendar-day rule. */
    static LocalDate today(User user) {
        return LocalDate.now(zoneOf(user));
    }

    static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    static Optional<User> optionalUser(UserRepository users, String userId) {
        return users.findById(userId);
    }
}
