package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallEdgeTest {

    private static final String FROM = "com.example.Caller#run()";
    private static final String TO = "com.example.Callee#target()";
    private static final int LINE = 42;

    @Test
    @DisplayName("carries the method ids, kind, resolution state, and call-site line")
    void carriesEdgeFields() {
        CallEdge edge = new CallEdge(FROM, TO, EdgeKind.CALL_EXTERNAL, true, LINE, null, null);

        assertThat(edge.from()).isEqualTo(FROM);
        assertThat(edge.to()).isEqualTo(TO);
        assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_EXTERNAL);
        assertThat(edge.resolved()).isTrue();
        assertThat(edge.line()).isEqualTo(LINE);
    }

    @Test
    @DisplayName("rejects a null from id")
    void rejectsNullFrom() {
        assertThatThrownBy(() -> new CallEdge(null, TO, EdgeKind.CALL_INTERNAL, true, LINE, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects a null to id")
    void rejectsNullTo() {
        assertThatThrownBy(() -> new CallEdge(FROM, null, EdgeKind.CALL_INTERNAL, true, LINE, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects a null kind")
    void rejectsNullKind() {
        assertThatThrownBy(() -> new CallEdge(FROM, TO, null, true, LINE, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("carries source and target module ids for a cross-module edge")
    void carriesModuleIdsForCrossModuleEdge() {
        CallEdge edge = new CallEdge(FROM, TO, EdgeKind.CROSS_MODULE, true, LINE, "kairos-api", "common");

        assertThat(edge.fromModuleId()).isEqualTo("kairos-api");
        assertThat(edge.toModuleId()).isEqualTo("common");
    }
}
