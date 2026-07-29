package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphTest {

    private static final String CALLER = "com.example.Caller#run()";
    private static final String CALLEE = "com.example.Callee#target()";
    private static final String OTHER_CALLER = "com.example.Other#run()";
    private static final int LINE = 10;

    @Nested
    @DisplayName("lookups")
    class Lookups {

        @Test
        @DisplayName("finds outgoing edges for a method")
        void findsOutgoingEdges() {
            CallEdge edge = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_EXTERNAL, true, LINE, null, null);
            CallGraph graph = new CallGraph(List.of(edge));

            assertThat(graph.outgoingFrom(CALLER)).containsExactly(edge);
            assertThat(graph.outgoingFrom(CALLEE)).isEmpty();
        }

        @Test
        @DisplayName("finds every caller of a method in O(1) via the reverse index")
        void findsIncomingEdges() {
            CallEdge fromCaller = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_EXTERNAL, true, LINE, null, null);
            CallEdge fromOtherCaller =
                    new CallEdge(OTHER_CALLER, CALLEE, EdgeKind.CALL_EXTERNAL, true, LINE, null, null);
            CallGraph graph = new CallGraph(List.of(fromCaller, fromOtherCaller));

            assertThat(graph.incomingTo(CALLEE)).containsExactlyInAnyOrder(fromCaller, fromOtherCaller);
            assertThat(graph.incomingTo(CALLER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("module dependency aggregation")
    class ModuleDependencyAggregation {

        @Test
        @DisplayName("rolls CROSS_MODULE edges up into a module-to-module dependency set")
        void aggregatesModuleDependencies() {
            CallEdge apiToCommon = new CallEdge(
                    CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");
            CallEdge adminToCommon = new CallEdge(
                    OTHER_CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-admin", "common");
            CallEdge internal = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_INTERNAL, true, LINE, null, null);
            CallGraph graph = new CallGraph(List.of(apiToCommon, adminToCommon, internal));

            Set<ModuleDependency> dependencies = graph.moduleDependencies();

            assertThat(dependencies).containsExactlyInAnyOrder(
                    new ModuleDependency("kairos-api", "common"),
                    new ModuleDependency("kairos-admin", "common"));
        }

        @Test
        @DisplayName("does not duplicate a module dependency backed by several edges")
        void deduplicatesRepeatedModulePairs() {
            CallEdge first = new CallEdge(CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");
            CallEdge second = new CallEdge(
                    OTHER_CALLER, CALLEE, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");
            CallGraph graph = new CallGraph(List.of(first, second));

            assertThat(graph.moduleDependencies()).containsExactly(new ModuleDependency("kairos-api", "common"));
        }

        @Test
        @DisplayName("reports no module dependencies for a project with no cross-module edges")
        void emptyWhenNoCrossModuleEdges() {
            CallEdge internal = new CallEdge(CALLER, CALLEE, EdgeKind.CALL_INTERNAL, true, LINE, null, null);
            CallGraph graph = new CallGraph(List.of(internal));

            assertThat(graph.moduleDependencies()).isEmpty();
        }
    }
}
