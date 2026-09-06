package com.bcconstructionservices.projects.mapper;

import com.bcconstructionservices.projects.dto.ProjectCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectResponse;
import com.bcconstructionservices.projects.dto.ProjectUpdateRequest;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectMapperTest {

    private ProjectMapper mapper;

    private UserLookupHelper userLookupHelper;

    @BeforeEach
    void setUp() {
        mapper = new ProjectMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);
    }

    private Project buildProject() {
        Project project = new Project();
        project.setId(12L);
        project.setCode("PRJ-2026-001");
        project.setName("Sta. Maria Warehouse Expansion");
        project.setDescription("New 500sqm warehouse extension");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setBudget(new BigDecimal("2500000.00"));
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setEndDate(LocalDate.of(2026, 12, 15));
        project.setInitiatedBy(3L);
        project.setCreatedAt(Instant.parse("2026-07-18T09:15:30Z"));
        project.setUpdatedAt(Instant.parse("2026-07-20T14:05:00Z"));
        return project;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapProjectToResponseWithAllFields() {
            when(userLookupHelper.resolveUserName(3L)).thenReturn("Juan Dela Cruz");
            Project project = buildProject();

            ProjectResponse response = mapper.toResponse(project);

            assertThat(response.getId()).isEqualTo(12L);
            assertThat(response.getCode()).isEqualTo("PRJ-2026-001");
            assertThat(response.getName()).isEqualTo("Sta. Maria Warehouse Expansion");
            assertThat(response.getDescription()).isEqualTo("New 500sqm warehouse extension");
            assertThat(response.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(response.getBudget()).isEqualByComparingTo("2500000.00");
            assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 15));
            assertThat(response.getInitiatedBy()).isEqualTo(3L);
            assertThat(response.getInitiatedByName()).isEqualTo("Juan Dela Cruz");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T09:15:30Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-20T14:05:00Z"));
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            ProjectCreateRequest request = ProjectCreateRequest.builder()
                    .code("PRJ-2026-002")
                    .name("Site Access Road")
                    .description("Gravel access road for the north site")
                    .budget(new BigDecimal("150000.00"))
                    .startDate(LocalDate.of(2026, 10, 1))
                    .endDate(LocalDate.of(2026, 10, 20))
                    .build();

            Project entity = mapper.toEntity(request);

            assertThat(entity.getCode()).isEqualTo("PRJ-2026-002");
            assertThat(entity.getName()).isEqualTo("Site Access Road");
            assertThat(entity.getDescription()).isEqualTo("Gravel access road for the north site");
            assertThat(entity.getBudget()).isEqualByComparingTo("150000.00");
            assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        }

        @Test
        void shouldNotSetServerManagedFieldsFromCreateRequest() {
            ProjectCreateRequest request = ProjectCreateRequest.builder()
                    .code("PRJ-2026-003")
                    .name("Some Project")
                    .startDate(LocalDate.of(2026, 9, 1))
                    .build();

            Project entity = mapper.toEntity(request);

            // code IS a plain 1:1 copy (same name/type both sides, so
            // MapStruct auto-maps it) — only status/initiatedBy/timestamps
            // are genuinely server-managed and ignored here.
            assertThat(entity.getCode()).isEqualTo("PRJ-2026-003");
            assertThat(entity.getId()).isNull();
            assertThat(entity.getInitiatedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteFieldsWithRequestValues() {
            Project existing = buildProject();

            ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                    .name("Updated Name")
                    .description("Updated description")
                    .budget(new BigDecimal("3000000.00"))
                    .startDate(LocalDate.of(2026, 9, 5))
                    .endDate(LocalDate.of(2027, 1, 10))
                    .build();

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getName()).isEqualTo("Updated Name");
            assertThat(existing.getDescription()).isEqualTo("Updated description");
            assertThat(existing.getBudget()).isEqualByComparingTo("3000000.00");
            assertThat(existing.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 5));
            assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        }

        @Test
        void shouldClearDescriptionAndEndDateWhenRequestSendsThemAsNull() {
            // Full-replacement PUT semantics — explicit null in the request
            // must clear the field, not leave it as-is.
            Project existing = buildProject();
            assertThat(existing.getDescription()).isNotNull();
            assertThat(existing.getEndDate()).isNotNull();

            ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                    .name("Same Name")
                    .description(null)
                    .budget(null)
                    .startDate(LocalDate.of(2026, 9, 1))
                    .endDate(null)
                    .build();

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getDescription()).isNull();
            assertThat(existing.getBudget()).isNull();
            assertThat(existing.getEndDate()).isNull();
        }

        @Test
        void shouldNeverChangeCodeOrStatus() {
            Project existing = buildProject();
            String originalCode = existing.getCode();
            ProjectStatus originalStatus = existing.getStatus();

            ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                    .name("Updated Name")
                    .startDate(LocalDate.of(2026, 9, 1))
                    .build();

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getCode()).isEqualTo(originalCode);
            assertThat(existing.getStatus()).isEqualTo(originalStatus);
        }
    }
}
