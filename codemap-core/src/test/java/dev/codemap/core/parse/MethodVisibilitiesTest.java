package dev.codemap.core.parse;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.codemap.core.model.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link MethodVisibilities#of} reads the declared visibility of a method or
 * constructor and degrades to {@link Visibility#PACKAGE} if that read fails,
 * so one odd declaration never fails the whole run (spec: degrade, don't fail).
 *
 * <p>A real {@link ClassOrInterfaceDeclaration}, parsed from a fixture source
 * string, stands in for the declaring type — {@link JavaSourceParserTest}
 * already exercises the happy paths (explicit/implicit visibility) against
 * real files. This class isolates {@link MethodVisibilities#of} itself,
 * including the one branch nothing else in the suite reaches: its
 * {@code catch (RuntimeException)} degrade clause.
 */
class MethodVisibilitiesTest {

    private static final String METHOD_NAME = "run";
    private static final String FAILURE_MESSAGE = "boom: modifiers could not be read";

    @Nested
    @DisplayName("degradation")
    class Degradation {

        @Test
        @DisplayName("returns PACKAGE without propagating when getAccessSpecifier() throws")
        void degradesToPackageWhenAccessSpecifierThrows() {
            ClassOrInterfaceDeclaration declaringType = classDeclaration("class Holder { }");
            MethodDeclaration throwingCallable = throwingAccessSpecifierMethod();

            Visibility visibility = MethodVisibilities.of(throwingCallable, declaringType);

            assertThat(visibility).isEqualTo(Visibility.PACKAGE);
        }

        @Test
        @DisplayName("does not let the RuntimeException escape the call")
        void doesNotPropagateTheException() {
            ClassOrInterfaceDeclaration declaringType = classDeclaration("class Holder { }");
            MethodDeclaration throwingCallable = throwingAccessSpecifierMethod();

            assertThatCode(() -> MethodVisibilities.of(throwingCallable, declaringType))
                    .doesNotThrowAnyException();
        }

        /**
         * A real {@link MethodDeclaration}, overridden only at the one call
         * {@link MethodVisibilities#of} makes that can fail
         * ({@code getAccessSpecifier()}, via {@code NodeWithModifiers}'s
         * default implementation over the node's modifier list) — every other
         * behaviour (name, node type) is the genuine JavaParser implementation.
         */
        private MethodDeclaration throwingAccessSpecifierMethod() {
            MethodDeclaration callable = new MethodDeclaration() {
                @Override
                public AccessSpecifier getAccessSpecifier() {
                    throw new IllegalStateException(FAILURE_MESSAGE);
                }
            };
            callable.setName(METHOD_NAME);
            return callable;
        }
    }

    @Nested
    @DisplayName("happy path (isolated from JavaSourceParserTest's full-file coverage)")
    class HappyPath {

        @Test
        @DisplayName("reads an explicit public modifier without needing the degrade branch")
        void readsExplicitVisibilityDirectly() {
            ClassOrInterfaceDeclaration declaringType = classDeclaration("class Holder { public void run() { } }");
            MethodDeclaration callable = declaringType.getMethodsByName(METHOD_NAME).get(0);

            Visibility visibility = MethodVisibilities.of(callable, declaringType);

            assertThat(visibility).isEqualTo(Visibility.PUBLIC);
        }
    }

    private ClassOrInterfaceDeclaration classDeclaration(String source) {
        CompilationUnit unit = StaticJavaParser.parse(source);
        return unit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new AssertionError("fixture must declare a class: " + source));
    }
}
