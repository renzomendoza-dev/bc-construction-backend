package com.bcconstructionservices.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against a real {@code ConflictingBeanDefinitionException} hit at
 * app startup: the projects module's exception handler was first named
 * {@code GlobalExceptionHandler}, the same simple class name inventory's
 * handler already used. Spring's default bean name is derived from the
 * simple class name, not the package, so two same-named {@code @Component}
 * -family classes in different modules collide the moment both are wired
 * into this app's context. No other test in the suite catches this class of
 * bug — every module-scoped test (`@DataJpaTest`, `@WebMvcTest`, etc.) only
 * ever loads that one module's slice of the context, never every module
 * wired together the way the real {@link BackendApplication} does — so this
 * replicates component-scan bean naming directly, without booting a full
 * (and much slower, DB-backed) Spring context.
 */
class ComponentScanBeanNameUniquenessTest {

    @Test
    void everyComponentFamilyClassHasAUniqueDefaultBeanNameAcrossAllModules() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true);
        AnnotationBeanNameGenerator nameGenerator = new AnnotationBeanNameGenerator();
        SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

        Map<String, String> beanNameToClassName = new TreeMap<>();
        StringBuilder collisions = new StringBuilder();

        for (BeanDefinition candidate : scanner.findCandidateComponents("com.bcconstructionservices")) {
            String className = candidate.getBeanClassName();
            String beanName = nameGenerator.generateBeanName(candidate, registry);

            String existing = beanNameToClassName.putIfAbsent(beanName, className);
            if (existing != null && !existing.equals(className)) {
                collisions.append("bean name \"").append(beanName).append("\" is used by both ")
                        .append(existing).append(" and ").append(className).append("\n");
            }
        }

        assertThat(collisions.toString())
                .as("Spring's default bean name is derived from the simple class name only — "
                        + "rename one of the colliding classes to a module-prefixed name")
                .isEmpty();
    }
}
