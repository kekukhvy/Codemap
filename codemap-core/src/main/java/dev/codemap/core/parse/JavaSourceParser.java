package dev.codemap.core.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.Layer;
import dev.codemap.core.model.TypeKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns one Java source file into indexed types and methods.
 *
 * <p>Parsing never throws for bad input. A file that will not parse is reported as
 * {@link ParsedFile#skipped}, because a real repository always holds something odd
 * and one such file must not cost the whole run.
 */
public final class JavaSourceParser {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceParser.class);

    private static final String DEFAULT_PACKAGE = "";
    private static final String NAME_SEPARATOR = ".";
    private static final String PARSE_FAILED = "could not be parsed";
    private static final String NO_POSITION = "declaration has no source position";

    private final JavaParser parser;
    private final MethodSignatures signatures = new MethodSignatures();

    /**
     * Creates a parser for the given language level.
     *
     * @param languageLevel syntax level to accept; a level above the running JVM
     *        is fine, since Codemap reads source rather than executing it
     */
    public JavaSourceParser(ParserConfiguration.LanguageLevel languageLevel) {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(languageLevel)
                // Comments are needed for Javadoc summaries.
                .setAttributeComments(true);
        this.parser = new JavaParser(configuration);
    }

    /**
     * Parses one file.
     *
     * @param projectRoot project directory, for reading the file
     * @param relativePath file path relative to {@code projectRoot}
     * @param moduleId module the file belongs to
     * @return the file's contribution, or a skip record with the reason
     */
    public ParsedFile parse(Path projectRoot, String relativePath, String moduleId) {
        Path absolute = projectRoot.resolve(relativePath);
        SourceText sourceText = SourceText.read(absolute);
        if (sourceText == null) {
            return ParsedFile.skipped(relativePath, "could not be read");
        }

        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(sourceText.content());
        } catch (RuntimeException e) {
            log.debug("Parser threw on {}: {}", relativePath, e.getMessage());
            return ParsedFile.skipped(relativePath, PARSE_FAILED);
        }

        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return ParsedFile.skipped(relativePath, PARSE_FAILED);
        }

        CompilationUnit unit = result.getResult().get();
        String packageName = unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString())
                .orElse(DEFAULT_PACKAGE);

        List<IndexedClass> classes = new ArrayList<>();
        List<IndexedMethod> methods = new ArrayList<>();

        OrphanJavadocIndex orphans = OrphanJavadocIndex.of(unit);

        for (TypeDeclaration<?> type : unit.getTypes()) {
            collectType(type, packageName, type.getNameAsString(), relativePath,
                    moduleId, sourceText, orphans, classes, methods);
        }
        return ParsedFile.parsed(relativePath, classes, methods);
    }

    /**
     * Records one type and recurses into the types nested inside it.
     *
     * <p>Nested types become their own index entries rather than being folded into
     * the enclosing one, since their methods are worth navigating to. Their names
     * use the dotted form a reader would write.
     */
    private void collectType(
            TypeDeclaration<?> type,
            String packageName,
            String nestedName,
            String file,
            String moduleId,
            SourceText sourceText,
            OrphanJavadocIndex orphans,
            List<IndexedClass> classes,
            List<IndexedMethod> methods) {

        if (type.getBegin().isEmpty() || type.getEnd().isEmpty()) {
            log.debug("Skipping {} in {}: {}", nestedName, file, NO_POSITION);
            return;
        }

        String fqn = packageName.isEmpty() ? nestedName : packageName + NAME_SEPARATOR + nestedName;
        String classId = fqn;

        classes.add(new IndexedClass(
                classId,
                moduleId,
                fqn,
                nestedName,
                packageName,
                kindOf(type),
                Layer.fromPackageOrName(packageName, nestedName),
                file,
                type.getBegin().get().line,
                type.getEnd().get().line,
                JavadocSummary.of(type, orphans)));

        addCanonicalRecordConstructor(type, classId, file, sourceText, methods);

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof CallableDeclaration<?> callable) {
                toMethod(callable, classId, file, sourceText, orphans).ifPresent(methods::add);
            } else if (member instanceof TypeDeclaration<?> nested) {
                collectType(nested, packageName, nestedName + NAME_SEPARATOR + nested.getNameAsString(),
                        file, moduleId, sourceText, orphans, classes, methods);
            }
        }
    }

    /**
     * Indexes the constructor a record gets for free.
     *
     * <p>The canonical constructor is generated by the compiler and never written
     * out, so nothing in the source represents it — yet {@code new Pagination(10, 0)}
     * is a real call, and leaving the target unindexed breaks the chain at every
     * record. It is recorded against the record's own declaration line, since that
     * is where a reader would look for it.
     *
     * <p>Skipped when the record declares the constructor explicitly, which the
     * normal member walk already handles.
     */
    private void addCanonicalRecordConstructor(
            TypeDeclaration<?> type, String classId, String file,
            SourceText sourceText, List<IndexedMethod> methods) {

        if (!type.isRecordDeclaration()) {
            return;
        }
        RecordDeclaration record = type.asRecordDeclaration();
        boolean explicitlyDeclared = record.getConstructors().stream()
                .anyMatch(constructor -> constructor.getParameters().size() == record.getParameters().size());
        if (explicitlyDeclared) {
            return;
        }

        String parameterTypes = record.getParameters().stream()
                .map(parameter -> signatures.simpleParameterType(parameter))
                .collect(java.util.stream.Collectors.joining(", "));

        int line = record.getBegin().map(position -> position.line).orElse(1);
        String simpleName = record.getNameAsString();

        methods.add(new IndexedMethod(
                classId + "#" + simpleName + "(" + parameterTypes + ")",
                classId,
                simpleName,
                simpleName + "(" + parameterTypes + ")",
                file,
                line,
                line,
                null,
                sourceText.slice(line, line),
                true));
    }

    /** Builds a method entry, including the source text the report will display. */
    private java.util.Optional<IndexedMethod> toMethod(
            CallableDeclaration<?> callable, String classId, String file,
            SourceText sourceText, OrphanJavadocIndex orphans) {

        if (callable.getBegin().isEmpty() || callable.getEnd().isEmpty()) {
            return java.util.Optional.empty();
        }
        int lineStart = callable.getBegin().get().line;
        int lineEnd = callable.getEnd().get().line;
        boolean constructor = callable instanceof ConstructorDeclaration;

        return java.util.Optional.of(new IndexedMethod(
                signatures.methodId(classId, callable),
                classId,
                ((NodeWithSimpleName<?>) callable).getNameAsString(),
                signatures.of(callable),
                file,
                lineStart,
                lineEnd,
                JavadocSummary.of(callable, orphans),
                sourceText.slice(lineStart, lineEnd),
                constructor));
    }

    private TypeKind kindOf(TypeDeclaration<?> type) {
        if (type.isEnumDeclaration()) {
            return TypeKind.ENUM;
        }
        if (type.isRecordDeclaration()) {
            return TypeKind.RECORD;
        }
        if (type.isAnnotationDeclaration()) {
            return TypeKind.ANNOTATION;
        }
        if (type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isInterface()) {
            return TypeKind.INTERFACE;
        }
        return TypeKind.CLASS;
    }

    /** Convenience for callers that do not care about the language level. */
    public static JavaSourceParser withLatestLanguageLevel() {
        return new JavaSourceParser(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }
}
