package ru.nstu.system.event.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Mints unique short-link slugs from an event title (spec "Уникальность короткой
 * ссылки"; task 7.3).
 *
 * <p>A slug is the transliterated, lower-cased title reduced to {@code [a-z0-9-]}
 * plus a short random suffix, so equal titles still yield distinct links. The
 * suffix is regenerated on collision, up to {@link #MAX_ATTEMPTS} times; the
 * unique index on {@code event.slug} is the last line of defence.</p>
 *
 * <p>The base is capped at {@link #MAX_BASE_LENGTH} characters so that the whole
 * slug can never exceed {@link #MAX_LENGTH} and violate the total length rule.</p>
 */
@Component
public class SlugGenerator {

    /** Maximum length of a slug, per the event-management spec. */
    public static final int MAX_LENGTH = 60;

    /** A valid slug: lower-case latin letters, digits and single dashes. */
    public static final Pattern VALID_SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    /** Base length leaving room for {@code -} plus {@link #SUFFIX_LENGTH} characters. */
    private static final int MAX_BASE_LENGTH = MAX_LENGTH - 1 - 6;

    private static final int SUFFIX_LENGTH = 6;

    private static final int MAX_ATTEMPTS = 8;

    private static final char[] SUFFIX_ALPHABET =
            "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"),
            Map.entry('г', "g"), Map.entry('д', "d"), Map.entry('е', "e"),
            Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"),
            Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"),
            Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"),
            Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "sch"),
            Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"));

    /** Fallback base for titles that contain no transliterable character. */
    private static final String FALLBACK_BASE = "event";

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a slug that does not yet exist.
     *
     * @param title  user-supplied title
     * @param exists collision predicate, normally {@code repository::existsBySlug}
     * @return a fresh, unique slug
     * @throws IllegalStateException when no unique slug could be minted in time
     */
    public String generate(String title, Predicate<String> exists) {
        Objects.requireNonNull(exists, "exists");
        String base = baseFrom(title);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = base + "-" + randomSuffix();
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique event slug after " + MAX_ATTEMPTS + " attempts");
    }

    /** @return the sanitised, length-capped base derived from {@code title} */
    public String baseFrom(String title) {
        String latin = transliterate(title == null ? "" : title);
        StringBuilder builder = new StringBuilder(latin.length());
        boolean previousDash = false;
        for (char character : latin.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                builder.append(character);
                previousDash = false;
            } else if (!previousDash && builder.length() > 0) {
                builder.append('-');
                previousDash = true;
            }
        }
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '-') {
            builder.deleteCharAt(builder.length() - 1);
        }
        String base = builder.isEmpty() ? FALLBACK_BASE : builder.toString();
        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH);
            while (base.endsWith("-")) {
                base = base.substring(0, base.length() - 1);
            }
        }
        return base;
    }

    private String randomSuffix() {
        char[] suffix = new char[SUFFIX_LENGTH];
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix[i] = SUFFIX_ALPHABET[random.nextInt(SUFFIX_ALPHABET.length)];
        }
        return new String(suffix);
    }

    private static String transliterate(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            String mapped = CYRILLIC.get(Character.toLowerCase(character));
            result.append(mapped != null ? mapped : character);
        }
        return result.toString();
    }
}
