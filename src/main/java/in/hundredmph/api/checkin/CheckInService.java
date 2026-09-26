package in.hundredmph.api.checkin;

import in.hundredmph.api.checkin.dto.CheckInRequest;
import in.hundredmph.api.checkin.dto.CheckInResponse;
import in.hundredmph.api.checkin.dto.CheckInSummaryResponse;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.domain.checkin.CheckIn;
import in.hundredmph.api.domain.checkin.CheckInRepository;
import in.hundredmph.api.domain.schedule.DayOfWeek;
import in.hundredmph.api.domain.session.SessionLog;
import in.hundredmph.api.domain.session.SessionLogRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.plan.PlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CheckInService {

    private final CheckInRepository checkIns;
    private final SessionLogRepository sessions;
    private final UserRepository users;
    private final PlanService plans;

    public CheckInService(CheckInRepository checkIns,
                          SessionLogRepository sessions,
                          UserRepository users,
                          PlanService plans) {
        this.checkIns = checkIns;
        this.sessions = sessions;
        this.users = users;
        this.plans = plans;
    }

    /** Writes or revises one day. PUT semantics: calling twice is calling once. */
    public CheckInResponse put(String userId, LocalDate date, CheckInRequest request) {
        User user = requireUser(userId);
        LocalDate day = date != null ? date : LocalDate.now(zoneOf(user));
        Instant now = Instant.now();

        // A member cannot check in for a day that has not happened yet.
        if (day.isAfter(LocalDate.now(zoneOf(user)))) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Cannot check in for a future date");
        }

        CheckIn checkIn = checkIns.findByUserIdAndLocalDate(userId, day).orElseGet(() -> {
            CheckIn created = new CheckIn();
            created.setUserId(userId);
            created.setLocalDate(day);
            created.setCreatedAt(now);
            return created;
        });

        checkIn.setCheckedIn(request.checkedIn() == null || request.checkedIn());
        checkIn.setPainScore(request.painScore());
        checkIn.setPainLocation(blankToNull(request.painLocation()));
        checkIn.setNote(blankToNull(request.note()));
        checkIn.setUpdatedAt(now);

        try {
            checkIns.save(checkIn);
        } catch (DuplicateKeyException ex) {
            // Raced with another device writing the same day; re-read the winner.
            return checkIns.findByUserIdAndLocalDate(userId, day)
                    .map(CheckInResponse::from)
                    .orElseThrow(() -> ApiException.of(ErrorCode.INTERNAL_ERROR, "Could not save the check-in"));
        }

        return CheckInResponse.from(checkIn);
    }

    public List<CheckInResponse> range(String userId, LocalDate from, LocalDate to) {
        List<CheckIn> found = (from != null && to != null)
                ? checkIns.findInRange(userId, from, to)
                : checkIns.findByUserIdOrderByLocalDateDesc(userId);
        return found.stream().map(CheckInResponse::from).toList();
    }

    public CheckInResponse forDate(String userId, LocalDate date) {
        return checkIns.findByUserIdAndLocalDate(userId, date)
                .map(CheckInResponse::from)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No check-in for that date"));
    }

    /**
     * The progress tab's numbers.
     *
     * <p>Streaks are computed here rather than on the device because "today" is
     * a different instant for every member, and two clients doing the same
     * arithmetic will eventually disagree.
     */
    public CheckInSummaryResponse summary(String userId) {
        User user = requireUser(userId);
        ZoneId zone = zoneOf(user);
        LocalDate today = LocalDate.now(zone);

        List<CheckIn> all = checkIns.findByUserIdOrderByLocalDateDesc(userId);
        Set<LocalDate> checkedDays = new HashSet<>();
        for (CheckIn checkIn : all) {
            if (checkIn.isCheckedIn()) checkedDays.add(checkIn.getLocalDate());
        }

        int currentStreak = streakEndingAt(checkedDays, today);
        int longestStreak = longestStreak(checkedDays);

        List<Integer> scores = all.stream()
                .map(CheckIn::getPainScore)
                .filter(java.util.Objects::nonNull)
                .toList();
        Double averagePain = scores.isEmpty()
                ? null
                : scores.stream().mapToInt(Integer::intValue).average().orElse(0);

        CheckIn latest = all.isEmpty() ? null : all.get(0);

        return new CheckInSummaryResponse(
                currentStreak,
                longestStreak,
                checkedDays.size(),
                (int) sessions.countByUserId(userId),
                averagePain,
                latest == null ? null : latest.getPainScore(),
                latest == null ? null : latest.getLocalDate(),
                adherence(user, today));
    }

    /**
     * Sessions completed as a share of sessions planned, over the last 28 days.
     * This is the practice's real health metric, so it is defined once, here.
     */
    private Double adherence(User user, LocalDate today) {
        LocalDate from = today.minusDays(27);
        int planned = 0;
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            if (plans.hasWorkOn(user.getId(), DayOfWeek.of(day))) {
                planned++;
            }
        }
        if (planned == 0) return null;

        long completed = sessions
                .findInRange(user.getId(), from, today)
                .stream()
                .map(SessionLog::getLocalDate)
                .distinct()
                .count();

        return Math.min(1.0, (double) completed / planned);
    }

    private static int streakEndingAt(Set<LocalDate> days, LocalDate today) {
        // A streak survives a day that has not ended yet: if there is no
        // check-in for today, count back from yesterday instead of zeroing.
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static int longestStreak(Set<LocalDate> days) {
        int longest = 0;
        for (LocalDate day : days) {
            // Only count from the start of a run, so each run is walked once.
            if (days.contains(day.minusDays(1))) continue;
            int length = 0;
            LocalDate cursor = day;
            while (days.contains(cursor)) {
                length++;
                cursor = cursor.plusDays(1);
            }
            longest = Math.max(longest, length);
        }
        return longest;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private User requireUser(String userId) {
        return users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));
    }

    private static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }
}
