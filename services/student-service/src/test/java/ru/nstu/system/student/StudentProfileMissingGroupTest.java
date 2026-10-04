package ru.nstu.system.student;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.nstu.system.student.domain.StudentProfile;
import ru.nstu.system.student.service.StudentProfileService;

/**
 * The identity spec scenario "Название группы отсутствует в справочнике": a
 * profile whose group has no directory entry must still be served, with the name
 * omitted and no error.
 *
 * <p>This state cannot be produced through the real schema because
 * {@code student_profile.group_id} has a foreign key to {@code app_group}, so the
 * profile service is stubbed here to exercise exactly the controller's fallback:
 * the lookup returns empty while the profile itself is valid.</p>
 */
class StudentProfileMissingGroupTest extends AbstractStudentIntegrationTest {

    @MockitoBean
    private StudentProfileService profileService;

    @Test
    void profileIsServedWithoutGroupNameWhenGroupIsNotInDirectory() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID orphanGroupId = UUID.randomUUID();
        StudentProfile profile = StudentProfile.create(accountId, AWAY_FULL_NAME, orphanGroupId);
        when(profileService.findProfile(accountId)).thenReturn(Optional.of(profile));
        when(profileService.findGroupName(orphanGroupId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.fullName").value(AWAY_FULL_NAME))
                .andExpect(jsonPath("$.groupId").value(orphanGroupId.toString()))
                .andExpect(jsonPath("$.groupName").value(nullValue()));
    }
}
