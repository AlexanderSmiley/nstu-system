package ru.nstu.system.student.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code student.student_profile}. The primary key is
 * the account id, so {@code findById} is the canonical account -> profile lookup.
 */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
}
