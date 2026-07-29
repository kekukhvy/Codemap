package dev.codemap.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EntryPoint} is a plain value type; the behaviour under test is its
 * required-field validation, which keeps a half-built entry point from ever
 * reaching {@code index.json}.
 */
class EntryPointTest {

    private static final String ID = "kairos-api:REST:POST:/api/v1/tasks";
    private static final String MODULE_ID = "kairos-api";
    private static final String LABEL = "POST /api/v1/tasks";
    private static final String METHOD_ID = "dev.kairos.api.task.TaskHandler#create(Context)";
    private static final SourceLocation SOURCE = new SourceLocation("Router.java", 43);

    @Test
    void carriesEveryFieldRequiredToRenderAndJoinAnEntryPoint() {
        EntryPoint entryPoint = new EntryPoint(
                ID, MODULE_ID, EntryPointKind.REST, LABEL, METHOD_ID, DetectedBy.RULE, SOURCE);

        assertThat(entryPoint.id()).isEqualTo(ID);
        assertThat(entryPoint.moduleId()).isEqualTo(MODULE_ID);
        assertThat(entryPoint.kind()).isEqualTo(EntryPointKind.REST);
        assertThat(entryPoint.label()).isEqualTo(LABEL);
        assertThat(entryPoint.methodId()).isEqualTo(METHOD_ID);
        assertThat(entryPoint.detectedBy()).isEqualTo(DetectedBy.RULE);
        assertThat(entryPoint.source()).isEqualTo(SOURCE);
    }

    @Test
    void rejectsAMissingMethodId() {
        assertThatThrownBy(() -> new EntryPoint(ID, MODULE_ID, EntryPointKind.REST, LABEL, null, DetectedBy.RULE, SOURCE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAMissingDetectedBy() {
        assertThatThrownBy(() -> new EntryPoint(ID, MODULE_ID, EntryPointKind.REST, LABEL, METHOD_ID, null, SOURCE))
                .isInstanceOf(NullPointerException.class);
    }
}
