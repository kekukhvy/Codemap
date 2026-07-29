package dev.codemap.core.parse;

import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.Layer;
import dev.codemap.core.model.TypeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the parser against real Java sources written into a temp directory.
 *
 * <p>Fixtures rather than mocks: a mocked AST would assert that the test's own
 * assumptions are self-consistent, while a real file proves JavaParser is being
 * driven correctly.
 */
class JavaSourceParserTest {

    private static final String FIXTURE = "/fixtures/Sample.java.txt";
    private static final String MODULE_ID = "app";
    private static final String SAMPLE_FQN = "com.example.domain.order.Sample";

    private final JavaSourceParser parser = JavaSourceParser.withLatestLanguageLevel();

    @TempDir
    Path projectRoot;

    private ParsedFile parsed;

    @BeforeEach
    void parseFixture() throws IOException {
        parsed = parse("Sample.java", readFixture());
    }

    @Nested
    @DisplayName("types")
    class Types {

        @Test
        @DisplayName("indexes the top-level type with its position and layer")
        void indexesTopLevelType() {
            IndexedClass sample = classNamed(SAMPLE_FQN);

            assertThat(sample.kind()).isEqualTo(TypeKind.CLASS);
            assertThat(sample.layer()).isEqualTo(Layer.DOMAIN);
            assertThat(sample.packageName()).isEqualTo("com.example.domain.order");
            assertThat(sample.moduleId()).isEqualTo(MODULE_ID);
            assertThat(sample.lineStart()).isLessThan(sample.lineEnd());
        }

        @Test
        @DisplayName("indexes nested types as their own entries, dotted rather than dollar-separated")
        void indexesNestedTypes() {
            assertThat(parsed.classes())
                    .extracting(IndexedClass::fqn)
                    .contains(SAMPLE_FQN + ".Builder", SAMPLE_FQN + ".Status",
                            SAMPLE_FQN + ".Line", SAMPLE_FQN + ".Visitor");
        }

        @Test
        @DisplayName("distinguishes classes, enums, records, and interfaces")
        void distinguishesTypeKinds() {
            assertThat(classNamed(SAMPLE_FQN + ".Status").kind()).isEqualTo(TypeKind.ENUM);
            assertThat(classNamed(SAMPLE_FQN + ".Line").kind()).isEqualTo(TypeKind.RECORD);
            assertThat(classNamed(SAMPLE_FQN + ".Visitor").kind()).isEqualTo(TypeKind.INTERFACE);
            assertThat(classNamed(SAMPLE_FQN + ".Builder").kind()).isEqualTo(TypeKind.CLASS);
        }
    }

    @Nested
    @DisplayName("methods")
    class Methods {

        @Test
        @DisplayName("keeps overloads distinct, so callers cannot be merged later")
        void keepsOverloadsDistinct() {
            List<IndexedMethod> overloads = parsed.methods().stream()
                    .filter(method -> method.name().equals("addLine"))
                    .toList();

            assertThat(overloads).hasSize(2);
            assertThat(overloads).extracting(IndexedMethod::id).doesNotHaveDuplicates();
            assertThat(overloads).extracting(IndexedMethod::signature)
                    .containsExactlyInAnyOrder("addLine(String) : void", "addLine(String, int) : void");
        }

        @Test
        @DisplayName("records the return type and varargs in the signature")
        void rendersSignatureDetail() {
            assertThat(methodNamed("lines").signature())
                    .isEqualTo("lines(int, String...) : List<String>");
        }

        @Test
        @DisplayName("marks constructors and names them after their type")
        void marksConstructors() {
            IndexedMethod constructor = parsed.methods().stream()
                    .filter(IndexedMethod::constructor)
                    .findFirst()
                    .orElseThrow();

            assertThat(constructor.name()).isEqualTo("Sample");
            assertThat(constructor.signature()).isEqualTo("Sample(String)");
        }

        @Test
        @DisplayName("captures the real source text, not a placeholder")
        void capturesRealSource() {
            IndexedMethod addLine = methodNamed("addLine");

            assertThat(addLine.source())
                    .contains("public void addLine(String sku)")
                    .contains("System.out.println(sku);");
        }

        @Test
        @DisplayName("attributes methods to the nested type that declares them")
        void attributesNestedMethods() {
            IndexedMethod build = methodNamed("build");

            assertThat(build.classId()).isEqualTo(SAMPLE_FQN + ".Builder");
        }
    }

    @Nested
    @DisplayName("javadoc")
    class Javadoc {

        @Test
        @DisplayName("keeps only the first sentence")
        void keepsFirstSentenceOnly() {
            assertThat(classNamed(SAMPLE_FQN).javadoc())
                    .isEqualTo("An order placed by a customer.");
        }

        @Test
        @DisplayName("captures method javadoc")
        void capturesMethodJavadoc() {
            assertThat(methodNamed("addLine").javadoc()).isEqualTo("Adds a line to the order.");
        }

        @Test
        @DisplayName("leaves javadoc null when a method has none")
        void leavesJavadocNullWhenAbsent() {
            assertThat(methodNamed("lines").javadoc()).isNull();
        }

        @Test
        @DisplayName("recovers a javadoc block separated from its type by a blank line")
        void recoversDetachedJavadoc() throws IOException {
            ParsedFile detached = parse("Detached.java", """
                    package com.example;

                    /**
                     * Documented despite the blank line below.
                     */

                    public class Detached {
                    }
                    """);

            assertThat(detached.classes()).singleElement()
                    .extracting(IndexedClass::javadoc)
                    .isEqualTo("Documented despite the blank line below.");
        }
    }

    @Nested
    @DisplayName("degradation")
    class Degradation {

        @Test
        @DisplayName("skips an unparseable file with a reason instead of throwing")
        void skipsUnparseableFile() throws IOException {
            ParsedFile broken = parse("Broken.java", "public class Broken { this is not java ###");

            assertThat(broken.wasSkipped()).isTrue();
            assertThat(broken.skipReason()).isNotBlank();
            assertThat(broken.classes()).isEmpty();
        }

        @Test
        @DisplayName("skips a missing file rather than failing the run")
        void skipsMissingFile() {
            ParsedFile missing = parser.parse(projectRoot, "Absent.java", MODULE_ID);

            assertThat(missing.wasSkipped()).isTrue();
        }

        @Test
        @DisplayName("indexes the enclosing method of an anonymous class without crashing")
        void handlesAnonymousClasses() throws IOException {
            ParsedFile withAnonymous = parse("Anon.java", """
                    package com.example;

                    public class Anon {
                        public Runnable make() {
                            return new Runnable() {
                                @Override
                                public void run() {
                                }
                            };
                        }
                    }
                    """);

            assertThat(withAnonymous.wasSkipped()).isFalse();
            assertThat(withAnonymous.classes()).extracting(IndexedClass::fqn)
                    .as("an anonymous class has no name to navigate to, so only its enclosing type is indexed")
                    .containsExactly("com.example.Anon");
            assertThat(withAnonymous.methods()).extracting(IndexedMethod::name).containsExactly("make");
        }

        @Test
        @DisplayName("parses a file with no types without producing anything")
        void handlesFileWithoutTypes() throws IOException {
            ParsedFile empty = parse("Empty.java", "package com.example;\n");

            assertThat(empty.wasSkipped()).isFalse();
            assertThat(empty.classes()).isEmpty();
            assertThat(empty.methods()).isEmpty();
        }
    }

    private ParsedFile parse(String fileName, String content) throws IOException {
        Files.writeString(projectRoot.resolve(fileName), content);
        return parser.parse(projectRoot, fileName, MODULE_ID);
    }

    private IndexedClass classNamed(String fqn) {
        return find(parsed.classes().stream().filter(indexed -> indexed.fqn().equals(fqn)).findFirst(), fqn);
    }

    private IndexedMethod methodNamed(String name) {
        return find(parsed.methods().stream().filter(method -> method.name().equals(name)).findFirst(), name);
    }

    private <T> T find(Optional<T> candidate, String what) {
        return candidate.orElseThrow(() -> new AssertionError("not indexed: " + what));
    }

    private String readFixture() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(stream).as("fixture %s must exist", FIXTURE).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
