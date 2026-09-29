package in.hundredmph.api.me.dto;

import jakarta.validation.constraints.NotBlank;

/** DELETE /v1/me. The password again, so a phone left unlocked cannot delete the account. */
public record DeleteAccountRequest(@NotBlank String password) {
}
