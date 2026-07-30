package dev.codemap.render;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

class SecAuditTest {

    @TempDir Path projectRoot;

    @Test
    void classSourcePayloadCannotBreakOut() throws Exception {
        // A real file on disk whose CLASS-level source carries the payloads.
        String classSrc = "package com.example;\n"
                + "// </script foo><img src=x onerror=alert(1)>\n"
                + "// </SCRIPT><svg onload=alert(2)>\n"
                + "// </script/><img src=x onerror=alert(3)>\n"
                + "// <!-- \u2028 \u2029 & -->\n"
                + "class Evil { void m() {} }\n";
        Path f = projectRoot.resolve("Evil.java");
        Files.writeString(f, classSrc);

        CodeIndex index = CodeIndex.builder()
                .root(projectRoot.toString())
                .modules(List.of(new IndexedModule("m", "m", "", List.of("src/main/java"))))
                .classes(List.of(new IndexedClass("com.example.Evil", "m", "com.example.Evil", "Evil",
                        "com.example", TypeKind.CLASS, Layer.ENTRY, "Evil.java", 1, 6, null)))
                .methods(List.of(new IndexedMethod("com.example.Evil#m()", "com.example.Evil", "m", "m()",
                        "Evil.java", 6, 6, null, "void m() {}", false, Visibility.PUBLIC, ChangeStatus.UNCHANGED)))
                .build();

        Path out = projectRoot.resolve("r.html");
        new ReportRenderer().render(CodemapOptions.builder().root(projectRoot).output(out).build(), index);
        String html = Files.readString(out);

        System.out.println("SEC: class source embedded? " + html.contains("\\u003c/script foo"));
        System.out.println("SEC: </script[\\s/] present? " + Pattern.compile("(?i)</script[\\s/]").matcher(html).find());
        System.out.println("SEC: </script> count = " + html.split(Pattern.quote("</script>"), -1).length);
        System.out.println("SEC: raw onerror= present? " + html.contains("onerror=alert"));
        System.out.println("SEC: raw U2028 in html? " + html.contains("\u2028"));
    }

    @Test
    void pathTraversalFromIndexFile() throws Exception {
        Path secret = projectRoot.getParent().resolve("outside-secret.txt");
        Files.writeString(secret, "TOP-SECRET-CANARY");

        CodeIndex index = CodeIndex.builder()
                .root(projectRoot.toString())
                .modules(List.of(new IndexedModule("m", "m", "", List.of("src/main/java"))))
                .classes(List.of(
                    new IndexedClass("A", "m", "a.A", "A", "a", TypeKind.CLASS, Layer.ENTRY,
                        "../outside-secret.txt", 1, 1, null),
                    new IndexedClass("B", "m", "b.B", "B", "b", TypeKind.CLASS, Layer.ENTRY,
                        "/etc/passwd", 1, 3, null),
                    new IndexedClass("C", "m", "c.C", "C", "c", TypeKind.CLASS, Layer.ENTRY,
                        "does/not/exist.java", 1, 3, null),
                    new IndexedClass("D", "m", "d.D", "D", "d", TypeKind.CLASS, Layer.ENTRY,
                        "", 1, 3, null)))
                .methods(List.of())
                .build();

        Path out = projectRoot.resolve("r2.html");
        new ReportRenderer().render(CodemapOptions.builder().root(projectRoot).output(out).build(), index);
        String html = Files.readString(out);
        System.out.println("SEC: traversal read outside root? " + html.contains("TOP-SECRET-CANARY"));
        System.out.println("SEC: absolute /etc/passwd read? " + html.contains("root:"));
        System.out.println("SEC: rendered without throwing = true");
    }
}
