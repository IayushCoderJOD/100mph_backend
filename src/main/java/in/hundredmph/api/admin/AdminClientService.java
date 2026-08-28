package in.hundredmph.api.admin;

import in.hundredmph.api.admin.dto.ClientDetailResponse;
import in.hundredmph.api.admin.dto.ClientSummaryResponse;
import in.hundredmph.api.assignment.AssignmentService;
import in.hundredmph.api.checkin.CheckInService;
import in.hundredmph.api.checkin.dto.CheckInResponse;
import in.hundredmph.api.checkin.dto.CheckInSummaryResponse;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.me.dto.UserDto;
import in.hundredmph.api.progression.ProgressionService;
import in.hundredmph.api.schedule.ScheduleService;
import in.hundredmph.api.schedule.dto.ScheduleResponse;
import in.hundredmph.api.session.SessionService;
import in.hundredmph.api.session.dto.SessionLogResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The coach's read of their practice.
 *
 * <p>"Needs attention" is defined once, here, rather than in the app: whether a
 * client is drifting is a clinical judgement the practice owns, and two clients
 * computing it separately would eventually disagree about who to call.
 */
@Service
public class AdminClientService {

    /** Below this share of planned sessions over four weeks, flag the client. */
    private static final double ADHERENCE_FLOOR = 0.6;
    /** A pain score at or above this is worth a look regardless of adherence. */
    private static final int PAIN_CEILING = 7;
    /** Silence for this many days is itself a signal. */
    private static final int QUIET_DAYS = 7;

    private final UserRepository users;
    private final ScheduleService schedules;
    private final SessionService sessions;
    private final CheckInService checkIns;
    private final AssignmentService assignments;
    private final ProgressionService progressions;

    public AdminClientService(UserRepository users,
                              ScheduleService schedules,
                              SessionService sessions,
                              CheckInService checkIns,
                              AssignmentService assignments,
                              ProgressionService progressions) {
        this.users = users;
        this.schedules = schedules;
        this.sessions = sessions;
        this.checkIns = checkIns;
        this.assignments = assignments;
        this.progressions = progressions;
    }

    /** The roster, quiet clients first — the list is a worklist. */
    public List<ClientSummaryResponse> roster() {
        return users.findByRole(UserRole.MEMBER).stream()
                .filter(user -> !user.isDeleted())
                .map(this::summarise)
                .sorted(Comparator
                        .comparing(ClientSummaryResponse::needsAttention).reversed()
                        .thenComparing(row -> row.user().fullName(),
                                Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public ClientDetailResponse detail(String userId) {
        User client = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such client"));

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(29);

        // Staff accounts have no program, and therefore no week to show.
        ScheduleResponse schedule =
                client.getActiveProgramId() == null ? null : schedules.forUser(userId);

        List<SessionLogResponse> recentSessions = sessions.history(userId, from, today);
        List<CheckInResponse> recentCheckIns = checkIns.range(userId, from, today);
        CheckInSummaryResponse summary = checkIns.summary(userId);

        return new ClientDetailResponse(
                UserDto.from(client),
                schedule,
                recentSessions,
                recentCheckIns,
                summary,
                assignments.forUser(userId),
                progressions.forUser(userId));
    }

    private ClientSummaryResponse summarise(User client) {
        CheckInSummaryResponse summary = checkIns.summary(client.getId());

        LocalDate lastActive = summary.latestCheckInDate();
        List<SessionLogResponse> recent = sessions.history(client.getId(), null, null);
        if (!recent.isEmpty()) {
            LocalDate lastSession = recent.get(0).localDate();
            if (lastActive == null || lastSession.isAfter(lastActive)) {
                lastActive = lastSession;
            }
        }

        int activeAssignments = assignments.forUser(client.getId()).size();

        boolean quiet = lastActive == null
                || lastActive.isBefore(LocalDate.now().minusDays(QUIET_DAYS));
        boolean drifting = summary.adherence() != null && summary.adherence() < ADHERENCE_FLOOR;
        boolean hurting = summary.latestPainScore() != null && summary.latestPainScore() >= PAIN_CEILING;

        return new ClientSummaryResponse(
                UserDto.from(client),
                summary.adherence(),
                summary.latestPainScore(),
                lastActive,
                summary.currentStreak(),
                activeAssignments,
                quiet || drifting || hurting);
    }
}
