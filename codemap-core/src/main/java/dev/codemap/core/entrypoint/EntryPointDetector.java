package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects entry points in one already-resolved compilation unit (spec §4.1).
 *
 * <p>Aggregates every {@link EntryPointRule}; each rule owns one recognition
 * shape, so adding a new one never touches the others. Rule-based detection only
 * — {@code --ai} and {@code codemap.yml} rules are layered on separately, never
 * overriding what a rule already found.
 */
public final class EntryPointDetector {

    private static final Set<String> JOB_ANNOTATIONS = Set.of("Scheduled");
    private static final Set<String> MESSAGE_ANNOTATIONS = Set.of("KafkaListener", "RabbitListener", "JmsListener");
    private static final Set<String> SOCKET_MESSAGE_ANNOTATIONS = Set.of("MessageMapping");

    private static final String JOB_LABEL_PREFIX = "job: ";
    private static final String MESSAGE_LABEL_PREFIX = "listener: ";
    private static final String SOCKET_LABEL_PREFIX = "socket: ";

    private final List<EntryPointRule> rules = List.of(
            new BootstrapEntryPointRule(),
            new RestAnnotationEntryPointRule(),
            new UiRouteEntryPointRule(),
            new ProgrammaticRouteEntryPointRule(),
            new MethodAnnotationEntryPointRule(EntryPointKind.JOB, JOB_LABEL_PREFIX, JOB_ANNOTATIONS),
            new MethodAnnotationEntryPointRule(EntryPointKind.MESSAGE, MESSAGE_LABEL_PREFIX, MESSAGE_ANNOTATIONS),
            new MethodAnnotationEntryPointRule(EntryPointKind.SOCKET, SOCKET_LABEL_PREFIX, SOCKET_MESSAGE_ANNOTATIONS),
            new ServerEndpointEntryPointRule());

    /**
     * Detects every entry point declared in a compilation unit.
     *
     * @param unit a compilation unit, parsed with a symbol solver attached
     * @param moduleId the module the file belongs to
     * @param relativePath the file's path relative to the project root
     * @return entry points found by rule, in declaration order
     */
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (EntryPointRule rule : rules) {
            entryPoints.addAll(rule.detect(unit, moduleId, relativePath));
        }
        return entryPoints;
    }
}
