package in.hundredmph.api.me.dto;

/** Server-driven feature flags, so a tab can be dark-launched without a release. */
public record FlagsDto(boolean learnTabEnabled) {
}
