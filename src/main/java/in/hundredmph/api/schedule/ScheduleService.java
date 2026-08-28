package in.hundredmph.api.schedule;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.content.model.DefaultScheduleEntry;
import in.hundredmph.api.domain.schedule.DayOfWeek;
import in.hundredmph.api.domain.schedule.WeeklySchedule;
import in.hundredmph.api.domain.schedule.WeeklyScheduleRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.schedule.dto.ScheduleResponse;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {

    private final WeeklyScheduleRepository schedules;
    private final UserRepository users;
    private final ContentCatalogue catalogue;

    public ScheduleService(WeeklyScheduleRepository schedules,
                           UserRepository users,
                           ContentCatalogue catalogue) {
        this.schedules = schedules;
        this.users = users;
        this.catalogue = catalogue;
    }

    /**
     * The member's week, seeded from the program's default the first time they
     * ask for it. Seeding on read rather than on program-assignment means a
     * member who was set up before this endpoint existed still gets a plan.
     */
    public ScheduleResponse forUser(String userId) {
        String programId = requireProgram(userId);
        WeeklySchedule schedule = schedules.findByUserIdAndProgramId(userId, programId)
                .orElseGet(() -> seedFromProgramDefault(userId, programId));
        return ScheduleResponse.of(programId, schedule.getDays());
    }

    /** Replaces the whole week. Validates every day before writing any of it. */
    public ScheduleResponse replace(String userId, Map<String, String> requested) {
        String programId = requireProgram(userId);
        Map<DayOfWeek, String> days = parseAndValidate(requested, programId);

        Instant now = Instant.now();
        WeeklySchedule schedule = schedules.findByUserIdAndProgramId(userId, programId)
                .orElseGet(() -> newSchedule(userId, programId, now));

        schedule.setDays(days);
        schedule.setUpdatedAt(now);
        schedules.save(schedule);

        return ScheduleResponse.of(programId, schedule.getDays());
    }

    /** Called when a member's program changes, so they land on a sensible week. */
    public void seedIfMissing(String userId, String programId) {
        if (schedules.findByUserIdAndProgramId(userId, programId).isEmpty()) {
            seedFromProgramDefault(userId, programId);
        }
    }

    /** The session type planned for a given day, for the session endpoints. */
    public String plannedSessionType(String userId, String programId, DayOfWeek day) {
        return schedules.findByUserIdAndProgramId(userId, programId)
                .map(schedule -> schedule.sessionTypeOn(day))
                .orElse(null);
    }

    // ---------------------------------------------------------------- helpers

    private Map<DayOfWeek, String> parseAndValidate(Map<String, String> requested, String programId) {
        if (requested == null) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "A schedule must include every day");
        }

        Map<DayOfWeek, String> days = new EnumMap<>(DayOfWeek.class);

        for (Map.Entry<String, String> entry : requested.entrySet()) {
            DayOfWeek day;
            try {
                day = DayOfWeek.from(entry.getKey());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Not a day of the week: " + entry.getKey(),
                        Map.of("day", entry.getKey()));
            }

            String sessionTypeId = entry.getValue();
            if (sessionTypeId == null || sessionTypeId.isBlank()) {
                days.put(day, null);
                continue;
            }

            // A member cannot schedule another program's session onto their week.
            if (!catalogue.sessionTypeBelongsTo(sessionTypeId, programId)) {
                throw new ApiException(ErrorCode.SCHEDULE_INVALID_DAY,
                        "session_type_id " + sessionTypeId + " does not belong to program " + programId,
                        Map.of("day", day.wire(), "session_type_id", sessionTypeId));
            }
            days.put(day, sessionTypeId);
        }

        // Days the client left out are rest, which is what an absent key means
        // in the app's own ScheduleMap.
        for (DayOfWeek day : DayOfWeek.ordered()) {
            days.putIfAbsent(day, null);
        }

        return days;
    }

    private WeeklySchedule seedFromProgramDefault(String userId, String programId) {
        Instant now = Instant.now();
        WeeklySchedule schedule = newSchedule(userId, programId, now);

        Map<DayOfWeek, String> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.ordered()) {
            days.put(day, null);
        }
        for (DefaultScheduleEntry entry : catalogue.defaultScheduleFor(programId)) {
            days.put(entry.dayOfWeek(), entry.sessionTypeId());
        }

        schedule.setDays(days);
        return schedules.save(schedule);
    }

    private WeeklySchedule newSchedule(String userId, String programId, Instant now) {
        WeeklySchedule schedule = new WeeklySchedule();
        schedule.setUserId(userId);
        schedule.setProgramId(programId);
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        return schedule;
    }

    private String requireProgram(String userId) {
        User user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));

        String programId = user.getActiveProgramId();
        if (programId == null) {
            throw ApiException.of(ErrorCode.NO_ACTIVE_PROGRAM,
                    "This account is not on a program");
        }
        return programId;
    }

    /** Exposed for the /me payload, which carries the week on the boot call. */
    public Map<String, String> daysFor(String userId, String programId) {
        WeeklySchedule schedule = schedules.findByUserIdAndProgramId(userId, programId)
                .orElseGet(() -> seedFromProgramDefault(userId, programId));

        Map<String, String> ordered = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.ordered()) {
            ordered.put(day.wire(), schedule.getDays().get(day));
        }
        return ordered;
    }
}
