package in.hundredmph.api.checkin.dto;

import in.hundredmph.api.domain.checkin.CheckIn;
import java.time.LocalDate;

public record CheckInResponse(
        String id,
        LocalDate localDate,
        boolean checkedIn,
        Integer painScore,
        String painLocation,
        String note) {

    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getLocalDate(),
                checkIn.isCheckedIn(),
                checkIn.getPainScore(),
                checkIn.getPainLocation(),
                checkIn.getNote());
    }
}
