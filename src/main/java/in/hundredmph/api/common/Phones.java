package in.hundredmph.api.common;

/** One normal form for phone numbers, so the same number cannot be stored twice in two spellings. */
public final class Phones {

    private Phones() {}

    /** Keeps the digits and a leading +, so "+91 90000 00000" stores comparably. Blank is null. */
    public static String normalise(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String cleaned = phone.trim().replaceAll("[^+0-9]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Between 7 and 15 digits — the E.164 ceiling — with at most one leading +. */
    public static boolean isPlausible(String normalised) {
        return normalised != null && normalised.matches("\\+?[0-9]{7,15}");
    }
}
