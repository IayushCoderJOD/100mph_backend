package in.hundredmph.api.common;

import org.springframework.http.HttpStatus;

/**
 * Stable machine strings the client maps to copy (README §7.1). The message on
 * the wire is for logs; these codes are the contract. Never rename one — add.
 */
public enum ErrorCode {

    VALIDATION_FAILED("validation_failed", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("malformed_request", HttpStatus.BAD_REQUEST),

    /**
     * Deliberately one code for "no such email" and "wrong password". Splitting
     * them would let anyone enumerate who has an account here.
     */
    INVALID_CREDENTIALS("invalid_credentials", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("token_invalid", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("token_expired", HttpStatus.UNAUTHORIZED),
    TOKEN_MISSING("token_missing", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID("refresh_token_invalid", HttpStatus.UNAUTHORIZED),
    /** A rotated-away token came back. The family is burned; sign in again. */
    REFRESH_TOKEN_REUSED("refresh_token_reused", HttpStatus.UNAUTHORIZED),

    ACCOUNT_SUSPENDED("account_suspended", HttpStatus.FORBIDDEN),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),
    NOT_ENTITLED("not_entitled", HttpStatus.FORBIDDEN),

    NOT_FOUND("not_found", HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_EXISTS("email_already_exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS("phone_already_exists", HttpStatus.CONFLICT),

    PASSWORD_TOO_WEAK("password_too_weak", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * Changing a password with the wrong current one. Deliberately not a 401:
     * the client reads 401 as an expired session and would sign the member out.
     */
    CURRENT_PASSWORD_INCORRECT("current_password_incorrect", HttpStatus.UNPROCESSABLE_ENTITY),
    RESET_TOKEN_INVALID("reset_token_invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    PROGRAM_NOT_FOUND("program_not_found", HttpStatus.UNPROCESSABLE_ENTITY),
    /** A session type was scheduled onto a program it does not belong to. */
    SCHEDULE_INVALID_DAY("schedule_invalid_day", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Staff have no program, so the training endpoints do not apply to them. */
    NO_ACTIVE_PROGRAM("no_active_program", HttpStatus.UNPROCESSABLE_ENTITY),
    SESSION_TYPE_NOT_FOUND("session_type_not_found", HttpStatus.UNPROCESSABLE_ENTITY),
    EXERCISE_NOT_FOUND("exercise_not_found", HttpStatus.UNPROCESSABLE_ENTITY),
    /** A file type or size the library does not take. */
    UNSUPPORTED_MEDIA("unsupported_media", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Attaching a key the bucket has nothing under — the upload never finished. */
    UPLOAD_NOT_FOUND("upload_not_found", HttpStatus.UNPROCESSABLE_ENTITY),
    /** This server has no bucket credentials, so it cannot take uploads. */
    MEDIA_NOT_CONFIGURED("media_not_configured", HttpStatus.SERVICE_UNAVAILABLE),
    /** Prescribing the same exercise to one client twice. */
    ALREADY_ASSIGNED("already_assigned", HttpStatus.CONFLICT),
    /** Deleting the practice's only admin account would leave nobody able to run it. */
    LAST_ADMIN("last_admin", HttpStatus.CONFLICT),

    TOO_MANY_ATTEMPTS("too_many_attempts", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }

    public HttpStatus status() { return status; }
}
