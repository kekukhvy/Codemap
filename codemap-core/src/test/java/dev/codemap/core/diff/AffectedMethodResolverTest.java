package dev.codemap.core.diff;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.CallGraph;
import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.EdgeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AffectedMethodResolver} marks the callers and callees of a changed
 * method {@code affected} — one call hop only, either direction (spec §5), the
 * highest-value status because it names code likely to break without
 * appearing in the diff.
 */
class AffectedMethodResolverTest {

    private static final String CALLER = "com.example.Caller#call()";
    private static final String CHANGED = "com.example.Changed#run()";
    private static final String CALLEE = "com.example.Callee#execute()";
    private static final String UNRELATED = "com.example.Unrelated#noop()";
    private static final String TWO_HOPS_AWAY = "com.example.FarAway#distant()";

    private final AffectedMethodResolver resolver = new AffectedMethodResolver();

    @Nested
    @DisplayName("one hop")
    class OneHop {

        @Test
        @DisplayName("marks the caller of a changed method affected")
        void marksCallerAffected() {
            CallGraph graph = new CallGraph(List.of(edge(CALLER, CHANGED)));

            Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

            assertThat(affected).containsExactly(CALLER);
        }

        @Test
        @DisplayName("marks the callee of a changed method affected")
        void marksCalleeAffected() {
            CallGraph graph = new CallGraph(List.of(edge(CHANGED, CALLEE)));

            Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

            assertThat(affected).containsExactly(CALLEE);
        }

        @Test
        @DisplayName("added and removed methods also radiate one hop of affected status")
        void addedMethodsAlsoRadiate() {
            CallGraph graph = new CallGraph(List.of(edge(CALLER, CHANGED)));

            Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.ADDED), graph);

            assertThat(affected).containsExactly(CALLER);
        }
    }

    @Nested
    @DisplayName("hop limit")
    class HopLimit {

        @Test
        @DisplayName("does not propagate past one hop in either direction")
        void doesNotPropagatePastOneHop() {
            CallGraph graph = new CallGraph(List.of(
                    edge(TWO_HOPS_AWAY, CALLER),
                    edge(CALLER, CHANGED)));

            Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

            assertThat(affected).containsExactly(CALLER);
            assertThat(affected).doesNotContain(TWO_HOPS_AWAY);
        }
    }

    @Nested
    @DisplayName("unrelated methods")
    class UnrelatedMethods {

        @Test
        @DisplayName("never marks a method with no edge to a changed method")
        void leavesUnrelatedMethodsAlone() {
            CallGraph graph = new CallGraph(List.of(edge(CALLER, CHANGED), edge(UNRELATED, UNRELATED)));

            Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED), graph);

            assertThat(affected).doesNotContain(UNRELATED);
        }

        @Test
        @DisplayName("never marks an already-changed method as affected too")
        void doesNotOverrideAnExistingStatus() {
            CallGraph graph = new CallGraph(List.of(edge(CALLER, CHANGED)));

            Set<String> affected = resolver.resolve(Map.of(CHANGED, ChangeStatus.CHANGED, CALLER, ChangeStatus.CHANGED), graph);

            assertThat(affected).doesNotContain(CALLER);
        }
    }

    private CallEdge edge(String from, String to) {
        return new CallEdge(from, to, EdgeKind.CALL_EXTERNAL, true, 1, null, null);
    }
}
