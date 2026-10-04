package ru.nstu.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.nstu.system.auth.service.PasswordGenerator;

/** Task 5.7: composition and uniqueness of generated temporary passwords. */
class PasswordGeneratorTest {

    private static final Set<Character> AMBIGUOUS = Set.of('0', 'O', '1', 'l', 'I');

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generatesTwelveCharactersWithLettersAndDigitsAndNoAmbiguousCharacters() {
        Set<String> unique = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String password = generator.generate();

            assertThat(password).hasSize(12);
            assertThat(password.chars().anyMatch(Character::isLetter))
                    .as("password contains a letter: %s", password).isTrue();
            assertThat(password.chars().anyMatch(Character::isDigit))
                    .as("password contains a digit: %s", password).isTrue();

            List<Character> characters = password.chars().mapToObj(value -> (char) value).toList();
            assertThat(characters)
                    .as("password has no ambiguous characters: %s", password)
                    .doesNotContainAnyElementsOf(AMBIGUOUS);

            unique.add(password);
        }

        assertThat(unique).hasSize(1000);
    }
}
