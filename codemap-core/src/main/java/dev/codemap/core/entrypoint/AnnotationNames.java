package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.expr.AnnotationExpr;

/**
 * Reads an annotation's own name regardless of how it was written at the use
 * site (spec §4.1 rule matching).
 *
 * <p>{@code AnnotationExpr.getNameAsString()} returns the name exactly as
 * spelled — {@code RestController} for an imported annotation, but the full
 * {@code org.springframework...RestController} when written fully qualified.
 * Every annotation-based rule cares only about the annotation's identity, not
 * which form the author chose, so this is shared rather than reimplemented per
 * rule.
 */
final class AnnotationNames {

    private AnnotationNames() {
    }

    static String simpleNameOf(AnnotationExpr annotation) {
        return SimpleNames.of(annotation.getNameAsString());
    }
}
