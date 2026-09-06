package com.bcconstructionservices.projects.repository;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProjectRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    private Project buildProject(String code, ProjectStatus status) {
        Project project = new Project();
        project.setCode(code);
        project.setName("Sta. Maria Warehouse Expansion");
        project.setStatus(status);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        return project;
    }

    @Nested
    class ExistsByCodeTests {

        @Test
        void shouldReturnTrueWhenCodeAlreadyExists() {
            projectRepository.saveAndFlush(buildProject("PRJ-001", ProjectStatus.ACTIVE));
            entityManager.clear();

            assertThat(projectRepository.existsByCode("PRJ-001")).isTrue();
        }

        @Test
        void shouldReturnFalseWhenCodeDoesNotExist() {
            assertThat(projectRepository.existsByCode("PRJ-999")).isFalse();
        }
    }

    @Nested
    class UniqueCodeConstraint {

        @Test
        void shouldThrowDataIntegrityViolationForDuplicateCode() {
            projectRepository.saveAndFlush(buildProject("PRJ-002", ProjectStatus.ACTIVE));
            entityManager.clear();

            Project duplicate = buildProject("PRJ-002", ProjectStatus.ACTIVE);

            assertThatThrownBy(() -> projectRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldFilterByStatus() {
            projectRepository.saveAndFlush(buildProject("PRJ-003", ProjectStatus.ACTIVE));
            projectRepository.saveAndFlush(buildProject("PRJ-004", ProjectStatus.ON_HOLD));
            entityManager.clear();

            List<Project> result = projectRepository
                    .search(ProjectStatus.ON_HOLD, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("PRJ-004");
        }

        @Test
        void shouldReturnAllProjectsWhenStatusFilterIsNull() {
            projectRepository.saveAndFlush(buildProject("PRJ-005", ProjectStatus.ACTIVE));
            projectRepository.saveAndFlush(buildProject("PRJ-006", ProjectStatus.COMPLETED));
            entityManager.clear();

            List<Project> result = projectRepository.search(null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class DefaultStatus {

        @Test
        void shouldDefaultStatusToActiveWhenNotExplicitlySet() {
            Project project = new Project();
            project.setCode("PRJ-007");
            project.setName("Default Status Test");
            project.setStartDate(LocalDate.of(2026, 9, 1));
            // status intentionally left unset — relies on the field initializer.

            Project saved = projectRepository.saveAndFlush(project);
            entityManager.clear();

            Project reloaded = projectRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        }
    }
}
