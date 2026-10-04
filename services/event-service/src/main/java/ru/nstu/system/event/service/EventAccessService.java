package ru.nstu.system.event.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.nstu.system.event.domain.Availability;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.RoleNames;

/**
 * Server-side event access rules (design.md D10; spec "Иерархия ролей",
 * "Уровни доступности события").
 *
 * <p>The role hierarchy is {@code ADMIN > STAFF > STUDENT > GUEST}. A token may
 * carry several roles; the highest rank wins. An event exposes a single required
 * rank through {@link Availability#requiredRank()}, so a viewer is allowed when
 * their rank is greater than or equal to it — the same comparison drives the
 * short-link check and the active-list filter.</p>
 */
@Service
public class EventAccessService {

    private static final Map<String, Integer> ROLE_RANKS = Map.of(
            RoleNames.ADMIN, 4,
            RoleNames.STAFF, 3,
            RoleNames.STUDENT, 2,
            RoleNames.GUEST, 1);

    /** @return {@code true} for a token carrying the {@code ADMIN} role */
    public boolean isAdmin(ParsedToken token) {
        return Objects.requireNonNull(token, "token").roles().contains(RoleNames.ADMIN);
    }

    /** @return {@code true} for a token carrying {@code STAFF} or {@code ADMIN} */
    public boolean isStaff(ParsedToken token) {
        return isAdmin(token) || token.roles().contains(RoleNames.STAFF);
    }

    /**
     * @return the highest rank among the token roles; {@code 0} when no known role
     *         is present (fail closed)
     */
    public int rank(ParsedToken token) {
        int rank = 0;
        for (String role : Objects.requireNonNull(token, "token").roles()) {
            rank = Math.max(rank, ROLE_RANKS.getOrDefault(role, 0));
        }
        return rank;
    }

    /** @return {@code true} when the token role outranks or matches the event availability */
    public boolean canView(ParsedToken token, Availability availability) {
        Objects.requireNonNull(availability, "availability");
        return rank(token) >= availability.requiredRank();
    }

    /**
     * @return the availability levels visible to the token role, in declaration
     *         order; used to build the active-list query (task 7.7)
     */
    public List<Availability> visibleAvailabilities(ParsedToken token) {
        int rank = rank(token);
        List<Availability> visible = new ArrayList<>();
        for (Availability availability : Availability.values()) {
            if (availability.requiredRank() <= rank) {
                visible.add(availability);
            }
        }
        return visible;
    }
}
