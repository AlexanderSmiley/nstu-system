package ru.nstu.system.auth.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates temporary passwords handed to the administrator exactly once
 * (design.md D9, identity spec "Создание пользователя администратором").
 *
 * <p>Rules: exactly {@value #LENGTH} characters, both letters and digits, and the
 * ambiguous characters {@code 0}, {@code O}, {@code 1}, {@code l} and {@code I}
 * are excluded so a password can be read aloud or typed without mistakes. The
 * alphabet is therefore 57 characters; randomness comes from
 * {@link SecureRandom}. Positions 0-2 are forced to an upper-case letter, a
 * lower-case letter and a digit, then the whole array is shuffled (Fisher-Yates)
 * so the forced characters do not reveal fixed positions.</p>
 *
 * <p>This bean is reused by account creation and password reset (group 5B).</p>
 */
@Component
public class PasswordGenerator {

    /** Password length required by the identity spec. */
    public static final int LENGTH = 12;

    static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I, O
    static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz"; // no l
    static final String DIGITS = "23456789"; // no 0, 1

    private static final String ALL = UPPERCASE + LOWERCASE + DIGITS;

    private final SecureRandom secureRandom;

    public PasswordGenerator() {
        this(new SecureRandom());
    }

    PasswordGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * @return a fresh temporary password that satisfies the password policy
     */
    public String generate() {
        char[] characters = new char[LENGTH];
        characters[0] = pick(UPPERCASE);
        characters[1] = pick(LOWERCASE);
        characters[2] = pick(DIGITS);
        for (int i = 3; i < LENGTH; i++) {
            characters[i] = pick(ALL);
        }
        shuffle(characters);
        return new String(characters);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(secureRandom.nextInt(alphabet.length()));
    }

    private void shuffle(char[] characters) {
        for (int i = characters.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char swap = characters[i];
            characters[i] = characters[j];
            characters[j] = swap;
        }
    }
}
