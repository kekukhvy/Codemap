package dev.codemap.core.graph;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.parse.JavaSourceParser;
import dev.codemap.core.parse.ParsedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link CallGraphBuilder} against small real Java fixtures written to a
 * temp directory, resolved through the real JavaParser symbol solver — never a
 * mocked AST, since the solver's behaviour is exactly what is under test.
 */
class CallGraphBuilderTest {

    private static final String MODULE_ID = "app";
    private static final String PACKAGE = "com.example";

    @TempDir
    Path projectRoot;

    private final JavaSourceParser parser = JavaSourceParser.withLatestLanguageLevel();
    private final List<ParsedFile> parsedFiles = new ArrayList<>();
    private IndexedModule module;

    @BeforeEach
    void setUpModule() {
        module = new IndexedModule(MODULE_ID, MODULE_ID, "", List.of("src/main/java"));
    }

    @Nested
    @DisplayName("same-class vs cross-class calls")
    class ClassBoundary {

        @Test
        @DisplayName("marks a call to another method of the same class CALL_INTERNAL")
        void sameClassCallIsInternal() throws IOException {
            writeClass("Greeter", """
                    package com.example;

                    public class Greeter {
                        public String greet(String name) {
                            return prefix() + name;
                        }

                        private String prefix() {
                            return "Hello, ";
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.Greeter#greet(String)");
                assertThat(edge.to()).isEqualTo("com.example.Greeter#prefix()");
                assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_INTERNAL);
                assertThat(edge.resolved()).isTrue();
            });
        }

        @Test
        @DisplayName("marks a call crossing into another class of the same module CALL_EXTERNAL")
        void crossClassCallIsExternal() throws IOException {
            writeClass("TaskHandler", """
                    package com.example;

                    public class TaskHandler {
                        private final CreateTaskUseCase createTaskUseCase;

                        public TaskHandler(CreateTaskUseCase createTaskUseCase) {
                            this.createTaskUseCase = createTaskUseCase;
                        }

                        public void create() {
                            createTaskUseCase.execute();
                        }
                    }
                    """);
            writeClass("CreateTaskUseCase", """
                    package com.example;

                    public class CreateTaskUseCase {
                        public void execute() {
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.TaskHandler#create()");
                assertThat(edge.to()).isEqualTo("com.example.CreateTaskUseCase#execute()");
                assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_EXTERNAL);
                assertThat(edge.resolved()).isTrue();
            });
        }

        /**
         * The receiver-type fallback counts only DECLARED methods, so an
         * inherited method of the same name is invisible to its "exactly one
         * candidate" check. A wrong guess here is worse than no edge: it is
         * stamped resolved:true, which the project's invariants reserve for
         * facts.
         */
        @Test
        @DisplayName("does not guess a target when an inherited method shares the name")
        void doesNotGuessAcrossInheritance() throws IOException {
            writeClass("Parent", """
                    package com.example;

                    public class Parent {
                        public void go(String only) { }
                    }
                    """);
            writeClass("Child", """
                    package com.example;

                    public class Child extends Parent {
                        public void go(int a, int b) { }
                    }
                    """);
            writeClass("Caller", """
                    package com.example;

                    public class Caller {
                        private final Child child = new Child();

                        public void call() {
                            this.child.go(unresolvable());
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges)
                    .filteredOn(edge -> edge.from().equals("com.example.Caller#call()"))
                    .filteredOn(CallEdge::resolved)
                    .noneMatch(edge -> edge.to().contains("go("));
        }

        @Test
        @DisplayName("keeps a call whose argument the solver cannot type-check")
        void unresolvableArgumentDoesNotLoseTheWholeCall() throws IOException {
            writeClass("TaskHandler", """
                    package com.example;

                    public class TaskHandler {
                        private final UpdateTaskUseCase updateTaskUseCase;

                        public TaskHandler(UpdateTaskUseCase updateTaskUseCase) {
                            this.updateTaskUseCase = updateTaskUseCase;
                        }

                        public void update() {
                            this.updateTaskUseCase.execute("id",
                                    unknownHelper(somethingElse()));
                        }
                    }
                    """);
            writeClass("UpdateTaskUseCase", """
                    package com.example;

                    public class UpdateTaskUseCase {
                        public void execute(String id, String payload) {
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.TaskHandler#update()");
                assertThat(edge.to()).isEqualTo("com.example.UpdateTaskUseCase#execute(String, String)");
                assertThat(edge.resolved()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("overload resolution")
    class Overloads {

        @Test
        @DisplayName("resolves two calls to different overloads as two distinct edges, not one")
        void distinguishesOverloadsByArgumentTypes() throws IOException {
            writeClass("Validation", """
                    package com.example;

                    public final class Validation {
                        public static String requireText(String value, String field, int maxLength) {
                            return value;
                        }

                        public static String requireText(String value, String field) {
                            return value;
                        }
                    }
                    """);
            writeClass("Task", """
                    package com.example;

                    import static com.example.Validation.requireText;

                    public class Task {
                        private String service;
                        private String name;

                        public Task(String service, String name) {
                            this.service = requireText(service, "service", 100);
                            this.name = requireText(name, "name");
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            List<CallEdge> requireTextEdges = edges.stream()
                    .filter(edge -> edge.from().equals("com.example.Task#Task(String, String)"))
                    .toList();

            assertThat(requireTextEdges).hasSize(2);
            assertThat(requireTextEdges).extracting(CallEdge::to).containsExactlyInAnyOrder(
                    "com.example.Validation#requireText(String, String, int)",
                    "com.example.Validation#requireText(String, String)");
            assertThat(requireTextEdges).allSatisfy(edge -> assertThat(edge.resolved()).isTrue());
        }
    }

    @Nested
    @DisplayName("out-of-project calls")
    class OutOfProject {

        @Test
        @DisplayName("produces no edge for a call resolved into the JDK")
        void jdkCallsProduceNoEdge() throws IOException {
            writeClass("Greeter", """
                    package com.example;

                    import java.util.ArrayList;
                    import java.util.List;

                    public class Greeter {
                        public List<String> names() {
                            List<String> names = new ArrayList<>();
                            names.add("Ada");
                            return names;
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).noneMatch(edge -> edge.to().contains("ArrayList") || edge.to().contains("add"));
        }
    }

    @Nested
    @DisplayName("degradation")
    class Degradation {

        @Test
        @DisplayName("degrades an unresolvable receiverless call to a name-based edge")
        void unresolvableReceiverlessCallDegradesToNameBasedEdge() throws IOException {
            writeClass("Greeter", """
                    package com.example;

                    public class Greeter extends UnknownBase {
                        public void greet() {
                            inheritedFromSomewhereUnknown();
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.Greeter#greet()");
                assertThat(edge.to()).isEqualTo("inheritedFromSomewhereUnknown");
                assertThat(edge.resolved()).isFalse();
            });
        }

        @Test
        @DisplayName("drops an unresolvable call made through a receiver, which is library noise")
        void dropsUnresolvableCallThroughReceiver() throws IOException {
            writeClass("Greeter", """
                    package com.example;

                    public class Greeter {
                        public void greet(Object dynamic) {
                            dynamic.somethingThatCannotBeResolved();
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges)
                    .as("an edge keyed by a bare method name can never join to an indexed method")
                    .noneSatisfy(edge -> assertThat(edge.to()).isEqualTo("somethingThatCannotBeResolved"));
        }
    }

    @Nested
    @DisplayName("recursion")
    class Recursion {

        @Test
        @DisplayName("produces a self edge for direct recursion without looping")
        void directRecursionProducesASelfEdge() throws IOException {
            writeClass("Factorial", """
                    package com.example;

                    public class Factorial {
                        public int of(int n) {
                            return n <= 1 ? 1 : n * of(n - 1);
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).hasSize(1);
            assertThat(edges).singleElement().satisfies(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.Factorial#of(int)");
                assertThat(edge.to()).isEqualTo("com.example.Factorial#of(int)");
                assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_INTERNAL);
            });
        }

        @Test
        @DisplayName("produces both edges of a mutual recursion without looping the build")
        void mutualRecursionProducesBothEdges() throws IOException {
            writeClass("Parity", """
                    package com.example;

                    public class Parity {
                        public boolean isEven(int n) {
                            return n == 0 || Parity.isOdd(n - 1);
                        }

                        static boolean isOdd(int n) {
                            return n != 0 && new Parity().isEven(n - 1);
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            // The constructor edge comes from the `new Parity()` in isOdd; a cycle
            // is recorded once in each direction rather than followed.
            assertThat(edges).extracting(CallEdge::from, CallEdge::to).contains(
                    org.assertj.core.groups.Tuple.tuple(
                            "com.example.Parity#isEven(int)", "com.example.Parity#isOdd(int)"),
                    org.assertj.core.groups.Tuple.tuple(
                            "com.example.Parity#isOdd(int)", "com.example.Parity#isEven(int)"));
            assertThat(edges).as("a cycle must not be expanded repeatedly").hasSize(3);
        }
    }

    @Nested
    @DisplayName("cross-module calls")
    class CrossModule {

        private static final String API_MODULE = "kairos-api";
        private static final String COMMON_MODULE = "common";

        @Test
        @DisplayName("marks a call crossing a module boundary CROSS_MODULE with both module ids")
        void crossModuleCallCarriesBothModuleIds() throws IOException {
            IndexedModule apiModule = new IndexedModule(
                    API_MODULE, API_MODULE, "kairos-api", List.of("kairos-api/src/main/java"));
            IndexedModule commonModule = new IndexedModule(
                    COMMON_MODULE, COMMON_MODULE, "common", List.of("common/src/main/java"));

            writeClassIn(apiModule, "TaskHandler", """
                    package com.example.api;

                    import com.example.common.Validation;

                    public class TaskHandler {
                        public void create(String name) {
                            Validation.requireText(name);
                        }
                    }
                    """);
            writeClassIn(commonModule, "Validation", """
                    package com.example.common;

                    public final class Validation {
                        public static void requireText(String value) {
                        }
                    }
                    """);

            CallGraphBuilder builder = new CallGraphBuilder(projectRoot, List.of(apiModule, commonModule));
            List<CallEdge> edges = builder.build(parsedFiles).callGraph().edges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.api.TaskHandler#create(String)");
                assertThat(edge.to()).isEqualTo("com.example.common.Validation#requireText(String)");
                assertThat(edge.kind()).isEqualTo(EdgeKind.CROSS_MODULE);
                assertThat(edge.fromModuleId()).isEqualTo(API_MODULE);
                assertThat(edge.toModuleId()).isEqualTo(COMMON_MODULE);
            });
        }
    }

    @Nested
    @DisplayName("uses-type edges")
    class UsesType {

        @Test
        @DisplayName("records a USES_TYPE edge for a parameter type declared in the project")
        void recordsParameterTypeUse() throws IOException {
            writeClass("TaskEdit", """
                    package com.example;

                    public record TaskEdit(String name) {
                    }
                    """);
            writeClass("Task", """
                    package com.example;

                    public class Task {
                        public void update(TaskEdit edit) {
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.Task#update(TaskEdit)");
                assertThat(edge.to()).isEqualTo("com.example.TaskEdit");
                assertThat(edge.kind()).isEqualTo(EdgeKind.USES_TYPE);
                assertThat(edge.resolved()).isTrue();
            });
        }

        @Test
        @DisplayName("records a USES_TYPE edge for a return type declared in the project")
        void recordsReturnTypeUse() throws IOException {
            writeClass("Task", """
                    package com.example;

                    public class Task {
                    }
                    """);
            writeClass("TaskRepository", """
                    package com.example;

                    public class TaskRepository {
                        public Task find() {
                            return null;
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.TaskRepository#find()");
                assertThat(edge.to()).isEqualTo("com.example.Task");
                assertThat(edge.kind()).isEqualTo(EdgeKind.USES_TYPE);
            });
        }

        @Test
        @DisplayName("produces no USES_TYPE edge for a JDK parameter type")
        void producesNoUsesTypeEdgeForJdkType() throws IOException {
            writeClass("Logger", """
                    package com.example;

                    public class Logger {
                        public void log(String message) {
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).noneMatch(edge -> edge.kind() == EdgeKind.USES_TYPE);
        }
    }

    @Nested
    @DisplayName("implements edges")
    class Implements {

        @Test
        @DisplayName("records an IMPLEMENTS edge from the interface to its implementation")
        void recordsImplementsEdge() throws IOException {
            writeClass("TaskRepository", """
                    package com.example;

                    public interface TaskRepository {
                        void save(String task);
                    }
                    """);
            writeClass("JooqTaskRepository", """
                    package com.example;

                    public class JooqTaskRepository implements TaskRepository {
                        public void save(String task) {
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            assertThat(edges).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.TaskRepository");
                assertThat(edge.to()).isEqualTo("com.example.JooqTaskRepository");
                assertThat(edge.kind()).isEqualTo(EdgeKind.IMPLEMENTS);
                assertThat(edge.resolved()).isTrue();
            });
        }

        @Test
        @DisplayName("resolves a port to its production implementation only, since test doubles are never indexed")
        void resolvesToProductionImplementationOnly() throws IOException {
            writeClass("TaskRepository", """
                    package com.example;

                    public interface TaskRepository {
                        void save(String task);
                    }
                    """);
            writeClass("JooqTaskRepository", """
                    package com.example;

                    public class JooqTaskRepository implements TaskRepository {
                        public void save(String task) {
                        }
                    }
                    """);

            List<CallEdge> edges = buildEdges();

            List<CallEdge> implementsEdges = edges.stream()
                    .filter(edge -> edge.kind() == EdgeKind.IMPLEMENTS)
                    .filter(edge -> edge.from().equals("com.example.TaskRepository"))
                    .toList();

            assertThat(implementsEdges).hasSize(1);
            assertThat(implementsEdges).extracting(CallEdge::to)
                    .containsExactly("com.example.JooqTaskRepository");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("records `new Foo(...)` as an edge to the constructor")
        void constructorInvocationProducesAnEdge() throws IOException {
            writeClass("Task", """
                    package com.example;

                    public class Task {
                        public Task(String name) {}
                    }
                    """);
            writeClass("Factory", """
                    package com.example;

                    public class Factory {
                        public Task build() {
                            return new Task("x");
                        }
                    }
                    """);

            assertThat(buildEdges())
                    .as("who constructs an object is part of the chain a reader follows")
                    .extracting(CallEdge::from, CallEdge::to)
                    .contains(org.assertj.core.groups.Tuple.tuple(
                            "com.example.Factory#build()", "com.example.Task#Task(String)"));
        }

        @Test
        @DisplayName("ignores construction of a type outside the project")
        void ignoresThirdPartyConstruction() throws IOException {
            writeClass("Factory", """
                    package com.example;

                    import java.util.ArrayList;

                    public class Factory {
                        public Object build() {
                            return new ArrayList<String>();
                        }
                    }
                    """);

            assertThat(buildEdges())
                    .extracting(CallEdge::to)
                    .noneMatch(target -> target.contains("ArrayList"));
        }
    }

    @Nested
    @DisplayName("id parity between the declaration site and the solver")
    class IdParity {

        /**
         * The guard that would have caught the varargs defect. Two independent
         * pieces of code build method ids — MethodSignatures from the declaration,
         * ResolvedMethodIds from the solver — and any disagreement makes an edge
         * point at a method that does not exist, silently.
         */
        @Test
        @DisplayName("every resolved edge target is a method that was actually indexed")
        void resolvedEdgesJoinToIndexedMethods() throws IOException {
            writeClass("Fixtures", """
                    package com.example;

                    import java.util.List;
                    import java.util.function.Function;

                    public class Fixtures {
                        public static <T> void withVarargs(String label, T... items) {}
                        public static void withGenerics(Function<String, Integer> mapper) {}
                        public static void withNestedGenerics(java.util.Map<String, List<Integer>> data) {}
                        public static void withArray(int[] values) {}
                    }
                    """);
            writeClass("Caller", """
                    package com.example;

                    import java.util.List;
                    import java.util.Map;
                    import java.util.function.Function;

                    public class Caller {
                        public void callThem() {
                            Fixtures.withVarargs("a", "b", "c");
                            Fixtures.withGenerics(String::length);
                            Fixtures.withNestedGenerics(Map.of());
                            Fixtures.withArray(new int[0]);
                        }
                    }
                    """);

            Set<String> indexedIds = parsedFiles.stream()
                    .flatMap(file -> file.methods().stream())
                    .map(IndexedMethod::id)
                    .collect(java.util.stream.Collectors.toSet());

            List<CallEdge> resolvedCalls = buildEdges().stream()
                    .filter(CallEdge::resolved)
                    .filter(edge -> edge.kind() == EdgeKind.CALL_INTERNAL
                            || edge.kind() == EdgeKind.CALL_EXTERNAL
                            || edge.kind() == EdgeKind.CROSS_MODULE)
                    .toList();

            assertThat(resolvedCalls).isNotEmpty();
            assertThat(resolvedCalls)
                    .as("an id built by the solver must match the one built at the declaration")
                    .allSatisfy(edge -> assertThat(indexedIds).contains(edge.to()));
        }

        @Test
        @DisplayName("a varargs method keeps its ellipsis on both sides")
        void varargsRenderIdentically() throws IOException {
            writeClass("Varargs", """
                    package com.example;

                    public class Varargs {
                        public static <T> void accept(String label, T... items) {}

                        public void use() {
                            accept("x", 1, 2);
                        }
                    }
                    """);

            assertThat(buildEdges())
                    .filteredOn(CallEdge::resolved)
                    .extracting(CallEdge::to)
                    .contains("com.example.Varargs#accept(String, T...)");
        }
    }

    private List<CallEdge> buildEdges() {
        CallGraphBuilder builder = new CallGraphBuilder(projectRoot, List.of(module));
        return builder.build(parsedFiles).callGraph().edges();
    }

    private void writeClass(String simpleName, String source) throws IOException {
        String relativePath = "src/main/java/" + PACKAGE.replace('.', '/') + "/" + simpleName + ".java";
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        parsedFiles.add(parser.parse(projectRoot, relativePath, MODULE_ID));
    }

    private void writeClassIn(IndexedModule targetModule, String simpleName, String source) throws IOException {
        String packagePath = source.lines()
                .filter(line -> line.startsWith("package "))
                .findFirst()
                .map(line -> line.substring("package ".length(), line.indexOf(';')).replace('.', '/'))
                .orElseThrow();
        String relativePath = targetModule.sourceRoots().get(0) + "/" + packagePath + "/" + simpleName + ".java";
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        parsedFiles.add(parser.parse(projectRoot, relativePath, targetModule.id()));
    }
}
