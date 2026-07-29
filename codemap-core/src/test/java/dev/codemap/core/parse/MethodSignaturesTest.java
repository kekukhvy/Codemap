package dev.codemap.core.parse;

import dev.codemap.core.model.IndexedMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link MethodSignatures} through the parser, against real
 * declarations covering generics, nested generics, arrays, and varargs — the
 * shapes most likely to break signature or id rendering.
 *
 * <p>Driven through {@link JavaSourceParser} rather than hand-built AST nodes:
 * {@code MethodSignatures} only ever receives declarations JavaParser produced,
 * so a fixture proves the real integration instead of a self-consistent mock.
 */
class MethodSignaturesTest {

    private static final String FILE_NAME = "Signatures.java";
    private static final String MODULE_ID = "app";
    private static final String CLASS_ID = "com.example.Signatures";
    private static final String PACKAGE_DECLARATION = "package com.example;\n";
    private static final String IMPORTS = """
            import java.util.List;
            import java.util.Map;
            """;

    private final JavaSourceParser parser = JavaSourceParser.withLatestLanguageLevel();

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("generics")
    class Generics {

        @Test
        @DisplayName("keeps a generic parameter type in both signature and id")
        void keepsGenericParameterType() throws IOException {
            IndexedMethod method = parseSingleMethod(
                    "public void store(List<String> items) {}");

            assertThat(method.signature()).isEqualTo("store(List<String>) : void");
            assertThat(method.id()).isEqualTo(CLASS_ID + "#store(List<String>)");
        }

        @Test
        @DisplayName("keeps nested generics distinct from their outer overload")
        void distinguishesNestedGenericsFromOuterOverload() throws IOException {
            List<IndexedMethod> methods = parseMethods(
                    "public void store(List<String> items) {}",
                    "public void store(Map<String, List<Integer>> items) {}");

            assertThat(methods).extracting(IndexedMethod::signature)
                    .containsExactlyInAnyOrder(
                            "store(List<String>) : void",
                            "store(Map<String,List<Integer>>) : void");
            assertThat(methods).extracting(IndexedMethod::id).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("renders a generic return type")
        void rendersGenericReturnType() throws IOException {
            IndexedMethod method = parseSingleMethod(
                    "public List<String> names() { return List.of(); }");

            assertThat(method.signature()).isEqualTo("names() : List<String>");
        }
    }

    @Nested
    @DisplayName("arrays")
    class Arrays {

        @Test
        @DisplayName("keeps array brackets in the parameter type")
        void keepsArrayBrackets() throws IOException {
            IndexedMethod method = parseSingleMethod("public void save(String[] rows) {}");

            assertThat(method.signature()).isEqualTo("save(String[]) : void");
            assertThat(method.id()).isEqualTo(CLASS_ID + "#save(String[])");
        }

        @Test
        @DisplayName("distinguishes an array overload from its scalar counterpart")
        void distinguishesArrayFromScalarOverload() throws IOException {
            List<IndexedMethod> methods = parseMethods(
                    "public void save(String row) {}",
                    "public void save(String[] rows) {}");

            assertThat(methods).extracting(IndexedMethod::id).doesNotHaveDuplicates();
            assertThat(methods).extracting(IndexedMethod::signature)
                    .containsExactlyInAnyOrder("save(String) : void", "save(String[]) : void");
        }
    }

    @Nested
    @DisplayName("varargs")
    class Varargs {

        @Test
        @DisplayName("marks a varargs parameter with an ellipsis in the signature")
        void marksVarargsInSignature() throws IOException {
            IndexedMethod method = parseSingleMethod("public void log(String... parts) {}");

            assertThat(method.signature()).isEqualTo("log(String...) : void");
        }

        @Test
        @DisplayName("distinguishes a varargs overload from its array counterpart by id")
        void distinguishesVarargsFromArrayOverloadById() throws IOException {
            List<IndexedMethod> methods = parseMethods(
                    "public void log(String[] parts) {}",
                    "public void log(String... parts) {}");

            assertThat(methods).extracting(IndexedMethod::signature)
                    .containsExactlyInAnyOrder("log(String[]) : void", "log(String...) : void");
        }
    }

    private IndexedMethod parseSingleMethod(String methodSource) throws IOException {
        List<IndexedMethod> methods = parseMethods(methodSource);
        assertThat(methods).hasSize(1);
        return methods.get(0);
    }

    private List<IndexedMethod> parseMethods(String... methodSources) throws IOException {
        StringBuilder body = new StringBuilder(PACKAGE_DECLARATION)
                .append(IMPORTS)
                .append("public class Signatures {\n");
        for (String methodSource : methodSources) {
            body.append(methodSource).append('\n');
        }
        body.append("}\n");

        Files.writeString(projectRoot.resolve(FILE_NAME), body.toString());
        ParsedFile parsed = parser.parse(projectRoot, FILE_NAME, MODULE_ID);
        return parsed.methods();
    }
}
