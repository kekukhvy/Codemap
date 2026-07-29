package dev.codemap.core.entrypoint;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises {@link EntryPointDetector} against small real Java fixtures, resolved
 * through the real JavaParser symbol solver — the constant-folding and
 * programmatic-route rules depend on real resolution, not a mocked AST.
 */
class EntryPointDetectorTest {

    private static final String MODULE_ID = "kairos-api";
    private static final String PACKAGE = "com.example";

    @TempDir
    Path projectRoot;

    private EntryPointDetector detector;

    @BeforeEach
    void setUp() {
        detector = new EntryPointDetector();
    }

    @Nested
    @DisplayName("BOOTSTRAP")
    class Bootstrap {

        @Test
        @DisplayName("recognises public static void main as a BOOTSTRAP entry point")
        void recognisesMainMethod() throws IOException {
            CompilationUnit unit = parse("Application", """
                    package com.example;

                    public class Application {
                        public static void main(String[] args) {
                            System.out.println("start");
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).hasSize(1);
            EntryPoint entryPoint = entryPoints.get(0);
            assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.BOOTSTRAP);
            assertThat(entryPoint.moduleId()).isEqualTo(MODULE_ID);
            assertThat(entryPoint.methodId()).isEqualTo("com.example.Application#main(String[])");
            assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
            assertThat(entryPoint.label()).contains("Application");
            assertThat(entryPoint.source().file()).isEqualTo(RELATIVE_PATH);
            assertThat(entryPoint.source().line()).isEqualTo(4);
        }

        @Test
        @DisplayName("does not treat a non-static main-named method as a BOOTSTRAP entry point")
        void ignoresInstanceMethodNamedMain() throws IOException {
            CompilationUnit unit = parse("NotAnEntryPoint", """
                    package com.example;

                    public class NotAnEntryPoint {
                        public void main() {
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH)).isEmpty();
        }
    }

    @Nested
    @DisplayName("REST — Spring annotations")
    class SpringRest {

        @Test
        @DisplayName("recognises a @GetMapping method on a @RestController as a REST entry point")
        void recognisesGetMapping() throws IOException {
            CompilationUnit unit = parse("TaskController", """
                    package com.example;

                    @org.springframework.web.bind.annotation.RestController
                    public class TaskController {
                        @org.springframework.web.bind.annotation.GetMapping("/api/v1/tasks")
                        public String list() {
                            return "[]";
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).hasSize(1);
            EntryPoint entryPoint = entryPoints.get(0);
            assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.REST);
            assertThat(entryPoint.label()).isEqualTo("GET /api/v1/tasks");
            assertThat(entryPoint.methodId()).isEqualTo("com.example.TaskController#list()");
            assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
        }

        @Test
        @DisplayName("ignores a mapped method when the class carries no controller annotation")
        void ignoresMappingWithoutControllerAnnotation() throws IOException {
            CompilationUnit unit = parse("PlainClass", """
                    package com.example;

                    public class PlainClass {
                        @org.springframework.web.bind.annotation.GetMapping("/api/v1/tasks")
                        public String list() {
                            return "[]";
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH)).isEmpty();
        }

        @Test
        @DisplayName("recognises imported (non-fully-qualified) mapping annotations")
        void recognisesImportedAnnotations() throws IOException {
            CompilationUnit unit = parse("DestinationController", """
                    package com.example;

                    import org.springframework.web.bind.annotation.PostMapping;
                    import org.springframework.web.bind.annotation.RestController;

                    @RestController
                    public class DestinationController {
                        @PostMapping("/api/v1/destinations")
                        public void create() {
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.label()).isEqualTo("POST /api/v1/destinations"));
        }
    }

    @Nested
    @DisplayName("REST — JAX-RS annotations")
    class JaxRs {

        @Test
        @DisplayName("recognises a JAX-RS @GET method combined with class- and method-level @Path")
        void recognisesJaxRsGet() throws IOException {
            CompilationUnit unit = parse("TaskResource", """
                    package com.example;

                    import javax.ws.rs.GET;
                    import javax.ws.rs.Path;

                    @Path("/api/v1/tasks")
                    public class TaskResource {
                        @GET
                        @Path("/{id}")
                        public String getById() {
                            return "{}";
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> {
                        assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.REST);
                        assertThat(entryPoint.label()).isEqualTo("GET /{id}");
                    });
        }
    }

    @Nested
    @DisplayName("UI — Vaadin @Route")
    class VaadinRoute {

        @Test
        @DisplayName("recognises a class-level @Route with a string literal as a UI entry point")
        void recognisesRouteWithLiteralValue() throws IOException {
            CompilationUnit unit = parse("TaskView", """
                    package com.example;

                    import com.vaadin.flow.router.Route;
                    import com.vaadin.flow.component.orderedlayout.VerticalLayout;

                    @Route("tasks")
                    public class TaskView extends VerticalLayout {
                        public void onShow() {
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).hasSize(1);
            EntryPoint entryPoint = entryPoints.get(0);
            assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.UI);
            assertThat(entryPoint.label()).isEqualTo("tasks");
            assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
        }

        @Test
        @DisplayName("names the root route rather than leaving the landing page unlabelled")
        void namesTheRootRoute() throws IOException {
            CompilationUnit unit = parse("DashboardView", """
                    package com.example;

                    import com.vaadin.flow.router.Route;

                    @Route("")
                    public class DashboardView {
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).hasSize(1);
            assertThat(entryPoints.get(0).label())
                    .as("an empty route is the landing page, the view a reader looks for first")
                    .isNotBlank();
        }

        @Test
        @DisplayName("resolves a @Route value that is a same-file constant field")
        void resolvesSameFileConstant() throws IOException {
            CompilationUnit unit = parse("ScheduleView", """
                    package com.example;

                    import com.vaadin.flow.router.Route;
                    import com.vaadin.flow.component.orderedlayout.VerticalLayout;

                    @Route(ScheduleView.SCHEDULES)
                    public class ScheduleView extends VerticalLayout {
                        static final String SCHEDULES = "schedules";
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.label()).isEqualTo("schedules"));
        }

        @Test
        @DisplayName("resolves a @Route(value = ...) constant declared in another class")
        void resolvesCrossFileConstant() throws IOException {
            writeAuxiliaryClass("DestinationRoutes", """
                    package com.example;

                    public final class DestinationRoutes {
                        public static final String DESTINATIONS = "destinations";
                    }
                    """);
            CompilationUnit unit = parse("DestinationView", """
                    package com.example;

                    import com.vaadin.flow.router.Route;
                    import com.vaadin.flow.component.orderedlayout.VerticalLayout;

                    @Route(value = DestinationRoutes.DESTINATIONS)
                    public class DestinationView extends VerticalLayout {
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.label()).isEqualTo("destinations"));
        }

        @Test
        @DisplayName("falls back to the expression text when a @Route value cannot be statically resolved")
        void fallsBackToExpressionTextWhenUnresolvable() throws IOException {
            CompilationUnit unit = parse("DynamicView", """
                    package com.example;

                    import com.vaadin.flow.router.Route;
                    import com.vaadin.flow.component.orderedlayout.VerticalLayout;

                    @Route(computeRoute())
                    public class DynamicView extends VerticalLayout {
                        static String computeRoute() {
                            return "dynamic";
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.label()).isEqualTo("computeRoute()"));
        }
    }

    @Nested
    @DisplayName("REST — programmatic Javalin routes")
    class ProgrammaticJavalinRoutes {

        @Test
        @DisplayName("recognises app.post(CONSTANT, Handler::method) as a REST entry point")
        void recognisesRouteRegisteredWithAMethodReference() throws IOException {
            writeAuxiliaryClass("TaskHandler", """
                    package com.example;

                    public class TaskHandler {
                        public void create(String requestBody) {
                        }
                    }
                    """);
            CompilationUnit unit = parse("Router", """
                    package com.example;

                    import io.javalin.Javalin;

                    public final class Router {
                        private static final String TASKS = "/api/v1/tasks";

                        public static void register(Javalin app, TaskHandler taskHandler) {
                            app.post(TASKS, taskHandler::create);
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).hasSize(1);
            EntryPoint entryPoint = entryPoints.get(0);
            assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.REST);
            assertThat(entryPoint.label()).isEqualTo("POST /api/v1/tasks");
            assertThat(entryPoint.methodId()).isEqualTo("com.example.TaskHandler#create(String)");
            assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
        }

        @Test
        @DisplayName("recognises every verb Javalin exposes, registered in one method")
        void recognisesEveryHttpVerb() throws IOException {
            writeAuxiliaryClass("TaskHandler", """
                    package com.example;

                    public class TaskHandler {
                        public void list(String requestBody) {}
                        public void create(String requestBody) {}
                        public void update(String requestBody) {}
                        public void delete(String requestBody) {}
                        public void patch(String requestBody) {}
                    }
                    """);
            CompilationUnit unit = parse("Router", """
                    package com.example;

                    import io.javalin.Javalin;

                    public final class Router {
                        private static final String TASKS = "/api/v1/tasks";
                        private static final String TASKS_BY_ID = "/api/v1/tasks/{id}";

                        public static void register(Javalin app, TaskHandler taskHandler) {
                            app.get(TASKS, taskHandler::list);
                            app.post(TASKS, taskHandler::create);
                            app.put(TASKS_BY_ID, taskHandler::update);
                            app.delete(TASKS_BY_ID, taskHandler::delete);
                            app.patch(TASKS_BY_ID, taskHandler::patch);
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).extracting(EntryPoint::label).containsExactlyInAnyOrder(
                    "GET /api/v1/tasks",
                    "POST /api/v1/tasks",
                    "PUT /api/v1/tasks/{id}",
                    "DELETE /api/v1/tasks/{id}",
                    "PATCH /api/v1/tasks/{id}");
        }

        @Test
        @DisplayName("does not treat a call on an unrelated receiver as a route registration")
        void ignoresCallsOnUnrelatedReceivers() throws IOException {
            CompilationUnit unit = parse("Logger", """
                    package com.example;

                    public class Logger {
                        public void log(java.util.List<String> messages) {
                            messages.get(0);
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH)).isEmpty();
        }

        @Test
        @DisplayName("falls back to the expression text when the path argument is not a literal or constant")
        void fallsBackToTextForADynamicPath() throws IOException {
            writeAuxiliaryClass("TaskHandler", """
                    package com.example;

                    public class TaskHandler {
                        public void list(String requestBody) {
                        }
                    }
                    """);
            CompilationUnit unit = parse("Router", """
                    package com.example;

                    import io.javalin.Javalin;

                    public final class Router {
                        public static void register(Javalin app, TaskHandler taskHandler, String prefix) {
                            app.get(prefix + "/tasks", taskHandler::list);
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.label()).isEqualTo("GET prefix + \"/tasks\""));
        }
    }

    @Nested
    @DisplayName("JOB — @Scheduled")
    class ScheduledJob {

        @Test
        @DisplayName("recognises a @Scheduled method as a JOB entry point")
        void recognisesScheduledMethod() throws IOException {
            CompilationUnit unit = parse("RetrySweeper", """
                    package com.example;

                    import org.springframework.scheduling.annotation.Scheduled;

                    public class RetrySweeper {
                        @Scheduled(fixedDelay = 5000)
                        public void sweep() {
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints).hasSize(1);
            EntryPoint entryPoint = entryPoints.get(0);
            assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.JOB);
            assertThat(entryPoint.methodId()).isEqualTo("com.example.RetrySweeper#sweep()");
            assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
        }
    }

    @Nested
    @DisplayName("MESSAGE — listener annotations")
    class MessageListener {

        @Test
        @DisplayName("recognises a @KafkaListener method as a MESSAGE entry point")
        void recognisesKafkaListener() throws IOException {
            CompilationUnit unit = parse("TaskEventsConsumer", """
                    package com.example;

                    import org.springframework.kafka.annotation.KafkaListener;

                    public class TaskEventsConsumer {
                        @KafkaListener(topics = "task-events")
                        public void onMessage(String payload) {
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> {
                        assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.MESSAGE);
                        assertThat(entryPoint.methodId()).isEqualTo("com.example.TaskEventsConsumer#onMessage(String)");
                    });
        }

        @Test
        @DisplayName("recognises a @RabbitListener method as a MESSAGE entry point")
        void recognisesRabbitListener() throws IOException {
            CompilationUnit unit = parse("DestinationEventsConsumer", """
                    package com.example;

                    import org.springframework.amqp.rabbit.annotation.RabbitListener;

                    public class DestinationEventsConsumer {
                        @RabbitListener(queues = "destination-events")
                        public void onMessage(String payload) {
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.MESSAGE));
        }
    }

    @Nested
    @DisplayName("SOCKET — @ServerEndpoint / @MessageMapping")
    class SocketEndpoint {

        @Test
        @DisplayName("recognises a @ServerEndpoint class's message-handling method as a SOCKET entry point")
        void recognisesServerEndpoint() throws IOException {
            CompilationUnit unit = parse("ScheduleSocket", """
                    package com.example;

                    import jakarta.websocket.OnMessage;
                    import jakarta.websocket.server.ServerEndpoint;

                    @ServerEndpoint("/ws/schedules")
                    public class ScheduleSocket {
                        @OnMessage
                        public void onMessage(String message) {
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> {
                        assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.SOCKET);
                        assertThat(entryPoint.methodId()).isEqualTo("com.example.ScheduleSocket#onMessage(String)");
                    });
        }

        @Test
        @DisplayName("recognises a @MessageMapping method as a SOCKET entry point")
        void recognisesMessageMapping() throws IOException {
            CompilationUnit unit = parse("ScheduleStompController", """
                    package com.example;

                    import org.springframework.messaging.handler.annotation.MessageMapping;

                    public class ScheduleStompController {
                        @MessageMapping("/schedules")
                        public void onScheduleMessage(String payload) {
                        }
                    }
                    """);

            assertThat(detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.SOCKET));
        }
    }

    private static final String RELATIVE_PATH =
            "src/main/java/" + PACKAGE.replace('.', '/') + "/Application.java";

    /** Writes a supporting class (e.g. a route-constants holder) without parsing it as the unit under test. */
    @Nested
    @DisplayName("degradation")
    class Degradation {

        /**
         * Detection runs over code whose types often cannot be resolved — a view
         * extending an absent framework base, a handler on an unknown receiver.
         * GUIDELINES calls these the rules most likely to regress, because they
         * fail silently rather than loudly.
         */
        @Test
        @DisplayName("skips a @Route view whose type cannot be resolved instead of failing")
        void skipsUnresolvableRouteView() throws IOException {
            CompilationUnit unit = parse("MysteryView", """
                    package com.example;

                    import com.vaadin.flow.router.Route;

                    @Route("mystery")
                    public class MysteryView extends SomeAbsentFrameworkBase {
                    }
                    """);

            assertThatCode(() -> detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips a route registration whose handler cannot be resolved")
        void skipsUnresolvableHandler() throws IOException {
            CompilationUnit unit = parse("Routes", """
                    package com.example;

                    import io.javalin.Javalin;

                    public class Routes {
                        public static void register(Javalin app, UnknownHandler handler) {
                            app.get("/things", handler::list);
                        }
                    }
                    """);

            assertThatCode(() -> detector.detect(unit, MODULE_ID, RELATIVE_PATH))
                    .as("an unresolvable handler must not end the run")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("keeps a route whose path constant lives in a class that is not indexed")
        void keepsRouteWithUnresolvablePathConstant() throws IOException {
            CompilationUnit unit = parse("Routes", """
                    package com.example;

                    import io.javalin.Javalin;

                    public class Routes {
                        public static void register(Javalin app, Handler handler) {
                            app.get(ExternalPaths.THINGS, handler::list);
                        }
                    }

                    class Handler {
                        public void list() {
                        }
                    }
                    """);

            List<EntryPoint> entryPoints = detector.detect(unit, MODULE_ID, RELATIVE_PATH);

            assertThat(entryPoints)
                    .as("an unresolvable path degrades to its expression text rather than dropping the route")
                    .isNotEmpty();
            assertThat(entryPoints.get(0).label()).contains("THINGS");
        }
    }

    private void writeAuxiliaryClass(String simpleName, String source) throws IOException {
        String relativePath = "src/main/java/" + PACKAGE.replace('.', '/') + "/" + simpleName + ".java";
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private CompilationUnit parse(String simpleName, String source) throws IOException {
        String relativePath = "src/main/java/" + PACKAGE.replace('.', '/') + "/" + simpleName + ".java";
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);

        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        solver.add(new JavaParserTypeSolver(projectRoot.resolve("src/main/java")));
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setSymbolResolver(new JavaSymbolSolver(solver));
        JavaParser parser = new JavaParser(configuration);
        return parser.parse(Files.readString(file)).getResult().orElseThrow();
    }
}
