package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.util.Optional;

/**
 * Reads a member expression off an annotation, whichever of the two forms
 * JavaParser represents it as (spec §4.1: {@code @Route("tasks")} versus
 * {@code @Route(value = "tasks")}).
 */
final class AnnotationMembers {

    private AnnotationMembers() {
    }

    /**
     * The expression assigned to one member of an annotation.
     *
     * @param annotation the annotation to read
     * @param memberName the member name to look for; ignored for a single-member
     *        annotation, whose one value is implicitly {@code value}
     * @return the member's expression, or empty when the annotation carries none
     */
    static Optional<Expression> valueOf(AnnotationExpr annotation, String memberName) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return Optional.of(single.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(memberName)) {
                    return Optional.of(pair.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
