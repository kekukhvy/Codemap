(function () {
  "use strict";

  const DATA = window.__CODEMAP_DATA__;

  const EDGE_KIND = {
    CALL_INTERNAL: "CALL_INTERNAL",
    CALL_EXTERNAL: "CALL_EXTERNAL",
    CROSS_MODULE: "CROSS_MODULE",
    USES_TYPE: "USES_TYPE",
    IMPLEMENTS: "IMPLEMENTS"
  };

  const CHANGE_STATUS = {
    ADDED: "ADDED",
    CHANGED: "CHANGED",
    REMOVED: "REMOVED",
    AFFECTED: "AFFECTED",
    UNCHANGED: "UNCHANGED"
  };

  const VISIBILITY = {
    PUBLIC: "PUBLIC",
    PROTECTED: "PROTECTED",
    PACKAGE: "PACKAGE",
    PRIVATE: "PRIVATE"
  };

  const VISIBILITY_MARKER = {
    PUBLIC: "+",
    PROTECTED: "#",
    PACKAGE: "~",
    PRIVATE: "-"
  };

  const ROOT_PAGE_LABEL = "/ (root)";
  const SOURCE_CLASS = "source";
  const TRANSITION_MS = 250;
  const LINK_STYLE = { SOLID: "solid", DASHED: "dashed" };

  /** Tags a member row with the method it renders, so a hovered link can find and highlight its endpoint rows (spec 007 §6.4.6). */
  const MEMBER_ROW_METHOD_ID_ATTRIBUTE = "data-method-id";
  const HOVERED_CSS_CLASS = "hovered";

  /** The strongest-first order a class box's own status resolves against its rows (spec 007 §3). */
  const STATUS_PRECEDENCE = [CHANGE_STATUS.ADDED, CHANGE_STATUS.CHANGED, CHANGE_STATUS.AFFECTED, CHANGE_STATUS.UNCHANGED];

  const ROOT_MARGIN_X = 40;
  const ROOT_MARGIN_Y = 40;

  /**
   * Indexes the raw view model for O(1) lookups the diagram and side panel
   * both need repeatedly: methods and classes by id, edges by source, and
   * edges by target (the "Called by" reverse lookup).
   */
  class CodemapIndex {
    constructor(data) {
      this.data = data;
      this.methodsById = new Map(data.methods.map((m) => [m.id, m]));
      this.classesById = new Map(data.classes.map((c) => [c.id, c]));
      this.modulesById = new Map(data.modules.map((m) => [m.id, m]));
      this.outgoingByFrom = groupBy(data.edges, (e) => e.from);
      this.incomingByTo = groupBy(data.edges, (e) => e.to);
      this.entryPointsByModule = groupBy(data.entryPoints, (e) => e.moduleId);
      this.methodsByClassId = groupBy(data.methods, (m) => m.classId);
      /** entryPointId -> strongest status anywhere it can reach; computed once. */
      this.reachableStatusCache = new Map();
    }

    /**
     * The strongest change status reachable from an entry point (spec 007 §3).
     *
     * <p>The handler's own status is not the interesting question: a PR often
     * leaves the handler untouched and rewrites the service beneath it. What the
     * reader wants from the list is "does this endpoint have anything to do with
     * the diff", which is a property of the whole reachable call chain.
     *
     * <p>Cached: this walks the graph, and the picker re-renders on every
     * keystroke in the search box.
     */
    reachableStatusOf(entryPoint) {
      const cached = this.reachableStatusCache.get(entryPoint.id);
      if (cached !== undefined) {
        return cached;
      }
      const seen = new Set([entryPoint.methodId]);
      const stack = [entryPoint.methodId];
      const statuses = [];
      while (stack.length > 0) {
        const methodId = stack.pop();
        const method = this.method(methodId);
        if (method && method.status) {
          statuses.push(method.status);
        }
        for (const edge of this.outgoing(methodId)) {
          if (edge.resolved && !seen.has(edge.to)) {
            seen.add(edge.to);
            stack.push(edge.to);
          }
        }
      }
      const strongest = strongestStatus(statuses);
      this.reachableStatusCache.set(entryPoint.id, strongest);
      return strongest;
    }

    method(id) {
      return this.methodsById.get(id);
    }

    classOf(id) {
      return this.classesById.get(id);
    }

    module(id) {
      return this.modulesById.get(id);
    }

    outgoing(id) {
      return this.outgoingByFrom.get(id) || [];
    }

    incoming(id) {
      return this.incomingByTo.get(id) || [];
    }

    entryPointsOf(moduleId) {
      return this.entryPointsByModule.get(moduleId) || [];
    }

    /** Every method a class declares, in index order — the class panel's method list. */
    methodsOfClass(classId) {
      return this.methodsByClassId.get(classId) || [];
    }

    /**
     * Whether a call target is drawable at all (spec 007 §4.4): the JDK,
     * third-party jars, and unresolved targets never become boxes — only a
     * call whose target method is itself indexed can.
     */
    isDrawableCallTarget(edge) {
      return edge.resolved && this.method(edge.to) !== undefined;
    }
  }

  function groupBy(items, keyFn) {
    const map = new Map();
    for (const item of items) {
      const key = keyFn(item);
      if (!map.has(key)) {
        map.set(key, []);
      }
      map.get(key).push(item);
    }
    return map;
  }

  /**
   * The strongest status among a set of member statuses (spec 007 §3):
   * `ADDED` > `CHANGED` > `AFFECTED` > `UNCHANGED`, and a missing/null status
   * never outranks `UNCHANGED`.
   */
  function strongestStatus(statuses) {
    for (const candidate of STATUS_PRECEDENCE) {
      if (statuses.includes(candidate)) {
        return candidate;
      }
    }
    return CHANGE_STATUS.UNCHANGED;
  }

  /** The UML visibility marker for a row (spec 007 §2.2): `+`/`#`/`~`/`-`. */
  function visibilityMarker(visibility) {
    return VISIBILITY_MARKER[visibility] || VISIBILITY_MARKER[VISIBILITY.PACKAGE];
  }

  function isPublicApi(method) {
    return method.visibility === VISIBILITY.PUBLIC || method.visibility === VISIBILITY.PROTECTED;
  }

  const STATUS_CSS_CLASS = {
    ADDED: "status-added",
    CHANGED: "status-changed",
    AFFECTED: "status-affected"
  };

  /** The UML `«stereotype»` for a class box's header (spec 007 §2.2): the layer, lower-cased. */
  function stereotypeLabel(layer) {
    return layer ? "«" + layer.toLowerCase() + "»" : "";
  }

  const TOKEN_CLASS_PREFIX = "tok-";
  const JAVA_WORD = /^[A-Za-z_$][A-Za-z0-9_$]*/;
  /**
   * Ordered: a keyword inside a comment or a string is not a keyword, so those
   * are consumed whole before anything else gets a chance to look inside them.
   */
  const JAVA_TOKEN_PATTERNS = [
    { kind: "comment", regex: /^\/\*[\s\S]*?(?:\*\/|$)/ },
    { kind: "comment", regex: /^\/\/[^\n]*/ },
    { kind: "string", regex: /^"""[\s\S]*?(?:"""|$)/ },
    { kind: "string", regex: /^"(?:\\.|[^"\\\n])*"?/ },
    { kind: "string", regex: /^'(?:\\.|[^'\\\n])*'?/ },
    { kind: "annotation", regex: /^@[A-Za-z_$][A-Za-z0-9_$]*/ },
    { kind: "number", regex: /^\d[\d_]*(?:\.[\d_]+)?[dDfFlL]?\b/ }
  ];
  const JAVA_KEYWORDS = new Set([
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "private", "protected", "public", "record", "return", "sealed", "short",
    "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
    "try", "var", "void", "volatile", "while", "yield", "true", "false", "null"
  ]);
  const ENTRY_POINTS_HIDDEN_CLASS = "entry-points-hidden";
  /** Bounds the side panel: narrow enough to be worth it, never swallowing the canvas. */
  const SIDE_PANEL_MIN_WIDTH = 260;
  const SIDE_PANEL_MAX_WIDTH = 900;
  const ENTRY_POINT_MARKER_CLASS = "entry-point-status";
  /** Explains the marker on hover, since a glyph alone does not say what it means. */
  const ENTRY_POINT_STATUS_TITLE = {
    ADDED: "New on this branch",
    CHANGED: "Changed on this branch",
    AFFECTED: "Calls something that changed"
  };
  const STATUS_GLYPH_CLASS = "status-glyph";
  /** The compartment set a collapsed box renders with: none at all. */
  const EMPTY_COMPARTMENTS = { constructors: [], publicMethods: [], revealedPrivateMethods: [] };
  /** Gap from the box's right edge to the first header control. */
  const HEADER_CONTROL_INSET = 22;
  /** Spacing between header controls, wide enough that their hit areas do not overlap. */
  const HEADER_CONTROL_PITCH = 26;
  const HEADER_HIT_AREA_CLASS = "header-hit-area";
  /** Padding either side of a header glyph that still counts as a click on it. */
  const HEADER_HIT_PAD_X = 5;
  const HEADER_HIT_HEIGHT = 22;
  /** Text sits above its baseline, so the hit area is lifted by roughly half a cap height. */
  const HEADER_HIT_BASELINE_DROP = 4;
  const COLLAPSE_TOGGLE_CLASS = "collapse-toggle";
  /** Shown when the box is open — clicking it folds the box away. */
  const COLLAPSE_GLYPH_OPEN = "(−)";
  /** Shown when the box is collapsed — clicking it brings the rows back. */
  const COLLAPSE_GLYPH_CLOSED = "(…)";
  const BOX_RECT_CLASS = "box-rect";
  const BOX_HEADER_FILL_CLASS = "box-header-fill";

  const STATUS_GLYPH_SYMBOL = {
    ADDED: "●", // filled circle
    CHANGED: "●", // filled circle — CHANGED reads the same as ADDED, distinguished by which rows are green
    AFFECTED: "▲" // triangle, visibly distinct from the ADDED/CHANGED circle
  };

  const STATUS_GLYPH_CSS_CLASS = {
    ADDED: "status-glyph-added",
    CHANGED: "status-glyph-changed",
    AFFECTED: "status-glyph-affected"
  };

  /**
   * The header status glyph (spec 007 §3): carries a box's change status
   * redundantly with its border colour/style, for readers who cannot rely on
   * colour alone. {@code null} when the box is `UNCHANGED` — nothing is drawn.
   *
   * @return {@code {symbol, cssClass}}, or {@code null}
   */
  function statusGlyph(status) {
    const symbol = STATUS_GLYPH_SYMBOL[status];
    if (!symbol) {
      return null;
    }
    return { symbol, cssClass: STATUS_GLYPH_CSS_CLASS[status] };
  }

/**
   * The strongest status a class box itself carries (spec 007 §3): the
   * strongest among its own status and every currently visible row's status.
   * Shared by the box's CSS classes and its header (status glyph, fill).
   */
  function boxStrongestStatus(ownStatus, visibleRowStatuses) {
    return strongestStatus([ownStatus, ...visibleRowStatuses]);
  }

  /**
   * The CSS classes for a class box's `<g>` (spec 007 §3): `class-box` plus,
   * when the strongest status among the box itself and its visible rows is
   * not `UNCHANGED`, exactly one `status-*` class — never more than one, so
   * the cascade cannot pick the wrong colour.
   */
  function boxCssClasses(ownStatus, visibleRowStatuses) {
    const strongest = boxStrongestStatus(ownStatus, visibleRowStatuses);
    const classes = ["class-box"];
    if (STATUS_CSS_CLASS[strongest]) {
      classes.push(STATUS_CSS_CLASS[strongest]);
    }
    return classes;
  }

  /** The CSS classes for one member row: its own status, and whether it is underlined (spec 007 §4.1, §4.3). */
  function rowCssClasses(status, underlined) {
    const classes = ["member-row"];
    if (STATUS_CSS_CLASS[status]) {
      classes.push(STATUS_CSS_CLASS[status]);
    }
    if (underlined) {
      classes.push("underlined");
    }
    return classes;
  }

  /**
   * The CSS classes for a routed link (spec 007 §6.4): solid (the default,
   * `class-link` alone) or dashed for a private call, with the existing
   * distinct cross-module style layered on top when the call crosses a
   * module boundary (spec §7).
   */
  function linkCssClasses(link) {
    const classes = ["class-link"];
    if (link.style === LINK_STYLE.DASHED) {
      classes.push("class-link-dashed");
    }
    if (link.crossModule) {
      classes.push("class-link-cross-module");
    }
    return classes;
  }

  /**
   * Splits a class's declared methods into the three UML compartments (spec
   * 007 §2.2): constructors, public API (PUBLIC/PROTECTED), and whichever
   * private/package rows this box's expanders have revealed so far. A
   * compartment with no rows is simply an empty array — the renderer omits
   * its rule.
   *
   * @param index the {@link CodemapIndex}
   * @param classId the class whose members to compose
   * @param revealedMethodIds a {@code Set} of method ids currently revealed
   *        in this box (spec 007 §2.3); private/package rows outside this set
   *        are never listed
   */
  function buildCompartments(index, classId, revealedMethodIds) {
    const methods = index.methodsOfClass(classId);
    const constructors = methods.filter((m) => m.constructor);
    const publicMethods = methods.filter((m) => !m.constructor && isPublicApi(m));
    const revealedPrivateMethods = methods.filter((m) =>
        !m.constructor && !isPublicApi(m) && revealedMethodIds.has(m.id));
    return { constructors, publicMethods, revealedPrivateMethods };
  }

  // ---------------------------------------------------------------------
  // Diagram state (spec 007 §6.1, §6.2): box uniqueness and refcounted
  // collapse, plus per-box private-row reveal tracking (§2.3).
  // ---------------------------------------------------------------------

  /** One box on the canvas: at most one instance ever exists per classId (spec 007 §6.1). */
  class ClassBox {
    constructor(classId) {
      this.classId = classId;
      /** Expander paths that revealed this box; removed only once this is empty (§6.2). */
      this.revealingPaths = new Set();
      /** method id -> Set of caller paths currently revealing that private row (§2.3). */
      this.revealedPrivateRowPaths = new Map();
      this.collapsed = true;
      this.position = null;
    }

    get revealedPrivateMethodIds() {
      const ids = new Set();
      for (const [methodId, paths] of this.revealedPrivateRowPaths) {
        if (paths.size > 0) {
          ids.add(methodId);
        }
      }
      return ids;
    }
  }

  /**
   * Joins a parent expander path to the row expanded beneath it. Collapsing a
   * path also retires everything nested under it, so this separator must not
   * occur inside a method id.
   */
  const PATH_SEPARATOR = ">";

  /**
   * Owns every box and the reference-counted bookkeeping that lets a shared
   * collaborator survive collapsing one of several callers (spec 007 §6.1,
   * §6.2). This class holds no D3/DOM state — it is the model the renderer
   * reads, kept separately so uniqueness and collapse rules are provable
   * without a canvas.
   */
  class DiagramState {
    constructor() {
      this.boxes = new Map();
    }

    /** Empties the canvas — used when switching to a different entry point. */
    clear() {
      this.boxes.clear();
    }

    /**
     * Returns the one box for `classId`, creating it on first reach. A
     * second (or later) expander path reaching an already-drawn class reuses
     * the same box instance rather than creating a duplicate (spec 007 §6.1)
     * — the caller still records its own path so collapse accounting stays
     * correct.
     */
    ensureBox(classId, revealingPath) {
      let box = this.boxes.get(classId);
      if (!box) {
        box = new ClassBox(classId);
        this.boxes.set(classId, box);
      }
      box.revealingPaths.add(revealingPath);
      return box;
    }

    boxFor(classId) {
      return this.boxes.get(classId);
    }

    /**
     * Retires one expander path. A box is removed only once no revealing
     * path remains (spec 007 §6.2) — a box still reachable from another
     * expanded path stays exactly where it is.
     */
    collapse(revealingPath) {
      for (const [classId, box] of this.boxes) {
        this.retirePaths(box.revealingPaths, revealingPath);
        this.forgetPrivateRowsFor(box, revealingPath);
        if (box.revealingPaths.size === 0) {
          this.boxes.delete(classId);
        }
      }
    }

    /**
     * Retires `revealingPath` and every path nested beneath it.
     *
     * <p>Paths are hierarchical — {@link #methodRowPath} builds them as
     * `parent > methodId` — so collapsing a row must also retire whatever was
     * expanded *through* it. Deleting only the exact string would strand every
     * descendant, leaving boxes on the canvas that no visible expander can ever
     * remove again.
     */
    retirePaths(paths, revealingPath) {
      const nestedPrefix = revealingPath + PATH_SEPARATOR;
      for (const path of [...paths]) {
        if (path === revealingPath || path.startsWith(nestedPrefix)) {
          paths.delete(path);
        }
      }
    }

    forgetPrivateRowsFor(box, revealingPath) {
      for (const paths of box.revealedPrivateRowPaths.values()) {
        this.retirePaths(paths, revealingPath);
      }
    }

    /** Reveals a private/package row in `box`, attributed to `callerPath` (spec 007 §2.3, §4.3.3). */
    revealPrivateRow(box, methodId, callerPath) {
      if (!box.revealedPrivateRowPaths.has(methodId)) {
        box.revealedPrivateRowPaths.set(methodId, new Set());
      }
      box.revealedPrivateRowPaths.get(methodId).add(callerPath);
    }

    /** Un-reveals a private row for one caller path; the row disappears once no caller path remains. */
    unrevealPrivateRow(box, methodId, callerPath) {
      const paths = box.revealedPrivateRowPaths.get(methodId);
      if (paths) {
        paths.delete(callerPath);
      }
    }
  }

  // ---------------------------------------------------------------------
  // Diagram interaction (spec 007 §4): entry-point clicks and the `(+)`
  // expander, both on a class header and on a method row. Builds on
  // {@link DiagramState} for the uniqueness/refcount rules and stays free of
  // D3/DOM so every expand/collapse rule is provable without a canvas.
  // ---------------------------------------------------------------------

  /** The expander path key for a method row's own expansion, scoped by the caller's own reveal path. */
  function methodRowPath(methodId, parentPath) {
    return parentPath + PATH_SEPARATOR + methodId;
  }

  /**
   * Drives box/link build-out from user interaction (spec 007 §4): opening
   * an entry point, the class-header expander ("what does this class use"),
   * and the method-row expander ("what does this method call"). Every reveal
   * is attributed to an expander path, so {@link DiagramState}'s reference
   * counting is what makes collapsing one path leave a shared box in place.
   */
  class DiagramController {
    constructor(index) {
      this.index = index;
      this.diagram = new DiagramState();
    }

    /**
     * Opens an entry point (spec 007 §4.1): draws its declaring class box and
     * a link from the pill to the entry method's row, with that row
     * underlined.
     */
    openEntryPoint(entryPointId) {
      const entryPoint = this.index.data.entryPoints.find((candidate) => candidate.id === entryPointId);
      const method = entryPoint && this.index.method(entryPoint.methodId);
      if (!method) {
        return null;
      }
      const box = this.diagram.ensureBox(method.classId, entryPointId);
      return {
        box,
        underlinedMethodId: method.id,
        link: { sourcePillId: entryPointId, targetClassId: method.classId, targetMethodId: method.id }
      };
    }

    /**
     * The class-header `(+)` expander (spec 007 §4.3): one box per distinct
     * collaborator class reached by any method this class declares, one link
     * per relationship — answers "what does this class use".
     */
    expandClassHeader(classId) {
      const path = "class:" + classId;
      const collaboratorClassIds = new Set();
      const links = [];
      for (const method of this.index.methodsOfClass(classId)) {
        for (const edge of this.index.outgoing(method.id)) {
          this.collectCollaborator(edge, classId, collaboratorClassIds, links);
        }
      }
      const boxes = [...collaboratorClassIds].map((collaboratorClassId) => this.diagram.ensureBox(collaboratorClassId, path));
      return { boxes, links };
    }

    collectCollaborator(edge, ownClassId, collaboratorClassIds, links) {
      if (!this.index.isDrawableCallTarget(edge)) {
        return;
      }
      const targetMethod = this.index.method(edge.to);
      if (targetMethod.classId === ownClassId || !isPublicApi(targetMethod)) {
        return;
      }
      collaboratorClassIds.add(targetMethod.classId);
      links.push({
        targetClassId: targetMethod.classId,
        targetMethodId: targetMethod.id,
        style: LINK_STYLE.SOLID,
        crossModule: edge.kind === EDGE_KIND.CROSS_MODULE
      });
    }

    /**
     * The method-row `(+)` expander (spec 007 §4.3): one link per resolved
     * outgoing call, following the three rules — public call to another
     * class draws that class's box (or reuses it) with a solid link to the
     * underlined target row; public call to this same class draws a solid
     * link within the box; private call to this same class draws a dashed
     * link and reveals the private row in this box.
     *
     * @param methodId the method being expanded
     * @param ownerClassId the class declaring `methodId`
     * @param parentPath the expander path this row itself was revealed
     *        under (the entry point id, or an enclosing method-row path),
     *        so this expansion's own path can be scoped beneath it
     */
    expandMethodRow(methodId, ownerClassId, parentPath) {
      const path = methodRowPath(methodId, parentPath);
      const links = [];
      for (const edge of this.index.outgoing(methodId)) {
        const link = this.expandOneCall(edge, ownerClassId, path);
        if (link) {
          links.push(link);
        }
      }
      return { links, path };
    }

    expandOneCall(edge, ownerClassId, path) {
      if (!this.index.isDrawableCallTarget(edge)) {
        return null;
      }
      const targetMethod = this.index.method(edge.to);
      if (targetMethod.classId !== ownerClassId) {
        return this.crossClassCall(targetMethod, path, edge.kind === EDGE_KIND.CROSS_MODULE);
      }
      return isPublicApi(targetMethod)
          ? this.sameClassPublicCall(targetMethod)
          : this.sameClassPrivateCall(targetMethod, ownerClassId, path);
    }

    crossClassCall(targetMethod, path, crossModule) {
      const box = this.diagram.ensureBox(targetMethod.classId, path);
      // A package-private or protected target is not in the box's public
      // compartment, so without revealing it the box would be drawn with no row
      // to attach to and the link would be dropped silently — 99 links on
      // Kairos, mostly calls into package-private application services.
      if (!isPublicApi(targetMethod)) {
        this.diagram.revealPrivateRow(box, targetMethod.id, path);
      }
      return {
        targetClassId: targetMethod.classId,
        targetMethodId: targetMethod.id,
        style: LINK_STYLE.SOLID,
        underlineTarget: true,
        crossModule
      };
    }

    sameClassPublicCall(targetMethod) {
      return {
        targetClassId: targetMethod.classId,
        targetMethodId: targetMethod.id,
        style: LINK_STYLE.SOLID,
        underlineTarget: true,
        selfLink: true
      };
    }

    sameClassPrivateCall(targetMethod, ownerClassId, path) {
      const box = this.diagram.ensureBox(ownerClassId, path);
      this.diagram.revealPrivateRow(box, targetMethod.id, path);
      return {
        targetClassId: ownerClassId,
        targetMethodId: targetMethod.id,
        style: LINK_STYLE.DASHED,
        selfLink: true
      };
    }

    /** Collapses a method row's expansion (spec 007 §4.3, "(−) collapses what that expander revealed"). */
    collapseMethodRow(methodId, parentPath) {
      this.diagram.collapse(methodRowPath(methodId, parentPath));
    }

    /** Collapses a class header's expansion. */
    collapseClassHeader(classId) {
      this.diagram.collapse("class:" + classId);
    }
  }

  // ---------------------------------------------------------------------
  // Layered-column placement (spec 007 §6.3): pure geometry functions, kept
  // free of D3/DOM so column assignment and ordering are provable in Node
  // without a canvas.
  // ---------------------------------------------------------------------

  /**
   * Assigns each box id to a column by breadth-first call depth from `rootId`
   * (spec 007 §6.3): the root sits in column 0, its direct links in column 1,
   * and so on. A box reachable at two different depths sits in the
   * shallowest one it was reached at, since BFS visits shallower links first
   * and a box's column is never revisited once assigned.
   *
   * @param boxIds every box id that must receive a column, including `rootId`
   * @param links {@code {source, target}} pairs, source/target are box ids
   * @param rootId the entry point's box id, column 0
   * @return {@code Map<boxId, columnIndex>}
   */
  function assignColumns(boxIds, links, rootId) {
    const outgoingByBoxId = groupBy(links, (link) => link.source);
    const columns = new Map([[rootId, 0]]);
    const queue = [rootId];
    while (queue.length > 0) {
      const current = queue.shift();
      const currentColumn = columns.get(current);
      const outgoing = outgoingByBoxId.get(current) || [];
      for (const link of outgoing) {
        if (!columns.has(link.target)) {
          columns.set(link.target, currentColumn + 1);
          queue.push(link.target);
        }
      }
    }
    for (const boxId of boxIds) {
      if (!columns.has(boxId)) {
        columns.set(boxId, 0);
      }
    }
    return columns;
  }

  /**
   * Orders one column's boxes by the average cross-axis position of the
   * parents that link into them (the standard barycentre heuristic for
   * reducing link crossings, spec 007 §6.3) — deterministic for a given
   * input, so re-layout never jitters. A box with no parent in `positions`
   * keeps a barycentre of {@link Number#POSITIVE_INFINITY} so it sorts last
   * rather than colliding at 0 with genuinely top-ranked boxes.
   *
   * @param boxIdsInColumn box ids currently in this column, any order
   * @param links every link in the diagram, {@code {source, target}}
   * @param parentPositions {@code {[parentBoxId]: crossAxisPosition}} of the
   *        previous column, already laid out
   * @return a new array, `boxIdsInColumn` sorted by ascending barycentre
   */
  function orderColumnByBarycentre(boxIdsInColumn, links, parentPositions) {
    const incomingByTarget = groupBy(links, (link) => link.target);
    const barycentreOf = (boxId) => {
      const parents = (incomingByTarget.get(boxId) || [])
          .map((link) => parentPositions[link.source])
          .filter((position) => position !== undefined);
      if (parents.length === 0) {
        return Number.POSITIVE_INFINITY;
      }
      return parents.reduce((sum, position) => sum + position, 0) / parents.length;
    };
    return [...boxIdsInColumn].sort((a, b) => {
      // Two parentless boxes both score POSITIVE_INFINITY, and Infinity-Infinity
      // is NaN — which is neither 0 nor a usable ordering, so comparing the
      // scores directly let the tie-break fall through and the order follow
      // whatever sequence the reader happened to click in.
      const left = barycentreOf(a);
      const right = barycentreOf(b);
      if (left !== right) {
        return left < right ? -1 : 1;
      }
      return a.localeCompare(b);
    });
  }

  // ---------------------------------------------------------------------
  // Orthogonal link routing (spec 007 §6.4, AC11): horizontal/vertical
  // segments only, lane offsets so parallel links never overlap, and no
  // segment crosses a box rectangle. Kept as pure geometry, independent of
  // D3, so "no segment intersects a box" and "no two links share a segment"
  // are provable without a canvas.
  // ---------------------------------------------------------------------

  const LANE_SPACING = 14;
  /** Grid cells the corridor search will consider before falling back. */
  const CORRIDOR_GRID_BUDGET = 6000;
  /** Cost of a corner, in pixels-equivalent: fewer turns read more clearly. */
  /** Clear space a routed line prefers to keep from any box edge. */
  const BOX_CLEARANCE = 6;
  /** Cost of running within BOX_CLEARANCE of a box rather than giving it room. */
  const GRAZE_PENALTY = 300;
  const TURN_PENALTY = 40;
  /** Cost of reusing a segment another link already claimed — steep, but not a ban. */
  const SHARED_SEGMENT_PENALTY = 4000;
  /** Pitch at which routes are sampled when reserving and testing occupancy. */
  const RESERVATION_PITCH = LANE_SPACING;
  /** How far beyond a route's own extent a box still shapes its grid. */
  const ROUTE_NEIGHBOURHOOD_PAD = 160;
  /** Attempts to push a box clear of its neighbours before giving up. */
  const OVERLAP_RESOLUTION_ATTEMPTS = 12;
  const SELF_LINK_LOOP_WIDTH = 36;

  /**
   * Routes one link as an orthogonal polyline (H/V segments only).
   *
   * <p>A regular link leaves the source row's right edge, runs to a lane
   * reserved for it in the gap between the two boxes, turns to the target
   * row's y, and enters the target's left edge. The lane is nudged clear of
   * any obstacle box whose rectangle would otherwise block the vertical
   * segment (spec 007 §6.4.3) — the lane grid guarantees a free corridor
   * exists between columns since boxes never occupy the inter-column gap.
   *
   * <p>A link that spans more than one column gap can still have an obstacle
   * sitting in a column *between* the source and target, straddling the
   * source or target row's own y — the vertical-run clearance above does not
   * protect the horizontal legs at row height in that case, since those legs
   * sweep across exactly the x range the obstacle occupies. When either leg
   * would cross such an obstacle, that leg is stepped: a short vertical hop
   * right next to the box edge, offset by this link's own lane so several
   * links leaving the very same row never collapse onto the same stub
   * segment (AC11), carries it clear of the obstacle's row band before it
   * turns onto the lane.
   *
   * <p>A self-link ({@code link.selfLink}) departs and re-enters the same
   * box's right edge, looping out and back with a small distinct loop rather
   * than degenerating into a zero-length link.
   *
   * @param link {@code {from: {rect, rowY}, to: {rect, rowY}, lane, selfLink}}
   * @param obstacles boxes (other than the link's own endpoints) whose
   *        rectangles a routed segment must not cross
   * @return an array of {@code {x, y}} points describing the polyline
   */
  /**
   * Routes one link, preferring a corridor-grid path that is provably clear of
   * every box (spec 007 §6.4.3, AC11) and steers away from segments already
   * taken by links routed before it.
   *
   * <p>`reserved` is a set of segment keys claimed by earlier links. Sharing one
   * is not forbidden — sometimes there is genuinely one way through — but it
   * costs, so the search only doubles up when the alternative is worse. That is
   * what a fixed point template could not do at all: it has no notion of what
   * any other link is doing, so parallel links collapsed onto one another.
   */
  function routeOrthogonalLink(link, obstacles, reserved) {
    if (link.selfLink) {
      return routeSelfLink(link, obstacles);
    }
    const searched = searchCorridorPath(link, obstacles, reserved || new Set());
    if (searched) {
      return searched;
    }
    return routeByTemplate(link, obstacles);
  }

  /**
   * Least-cost orthogonal path over a grid built from the box edges.
   *
   * <p>Every candidate segment is checked against every obstacle, so a path that
   * exists is clear by construction rather than by after-the-fact detours. Cost
   * is distance plus a turn penalty (fewer corners read better) plus a heavy
   * penalty for reusing a segment another link already claimed — which is what
   * keeps parallel links on separate tracks without hard-coding a lane shape.
   *
   * <p>The source and target boxes join the obstacle set with their attachment
   * row punched through, so a path may reach that one row but cannot cross the
   * box anywhere else.
   *
   * @return the polyline, or `null` if the grid admits no clear path
   */
  function searchCorridorPath(link, obstacles, reserved) {
    const start = { x: link.from.rect.x + link.from.rect.width, y: link.from.rowY };
    // The endpoint boxes are solid walls, not walls with a slot at the
    // attachment row: a slot let a path enter one side and come out the other,
    // sweeping the whole box on the way. The path starts on the source's edge
    // and finishes on the target's, so it never needs to be inside either.
    const walls = obstacles
        .concat([{ rect: link.from.rect }, { rect: link.to.rect }]);

    // Both target edges are viable; the search picks whichever is cheaper, which
    // is how forward, same-column and backward links stay one code path.
    const ends = [
      { x: link.to.rect.x, y: link.to.rowY },
      { x: link.to.rect.x + link.to.rect.width, y: link.to.rowY }
    ];

    // Only boxes near the route shape the grid. A whole 50-box diagram yields a
    // grid of over a million cells — the search then exceeded its budget and
    // bailed out on every link, silently handing all of them to the template.
    const near = wallsNearRoute(walls, start, ends);
    const xs = gridLines([start.x, ...ends.map((e) => e.x)], near, link.lane, (r) => [r.x, r.x + r.width]);
    const ys = gridLines([start.y, ...ends.map((e) => e.y)], near, link.lane, (r) => [r.y, r.y + r.height]);
    if (xs.length * ys.length > CORRIDOR_GRID_BUDGET) {
      return null;
    }

    let best = null;
    const blocked = new Map();
    for (const end of ends) {
      const path = dijkstraGrid(start, end, xs, ys, walls, reserved, blocked);
      if (path && (!best || path.cost < best.cost)) {
        best = path;
      }
    }
    return best ? dedupeConsecutivePoints(dropCollinearPoints(best.points)) : null;
  }

  /**
   * The walls whose edges are worth turning into grid lines: those overlapping
   * the route's bounding box, generously padded.
   *
   * <p>Collision testing still uses every wall — this only decides which ones
   * contribute candidate lines, since the grid is the product of both axes and
   * grows quadratically with them.
   */
  function wallsNearRoute(walls, start, ends) {
    const pad = ROUTE_NEIGHBOURHOOD_PAD;
    const minX = Math.min(start.x, ...ends.map((end) => end.x)) - pad;
    const maxX = Math.max(start.x, ...ends.map((end) => end.x)) + pad;
    const minY = Math.min(start.y, ...ends.map((end) => end.y)) - pad;
    const maxY = Math.max(start.y, ...ends.map((end) => end.y)) + pad;
    return walls.filter((wall) => wall.rect.x <= maxX && wall.rect.x + wall.rect.width >= minX
        && wall.rect.y <= maxY && wall.rect.y + wall.rect.height >= minY);
  }

  /**
   * Grid lines: the fixed endpoints, every nearby box edge pushed out by this
   * link's lane offset, and a track either side of each endpoint.
   *
   * <p>Several offsets rather than only this link's own: the search needs
   * somewhere else to go when its first choice is taken, and a grid derived from
   * a single offset gives parallel links no alternative but to overlap.
   */
  function gridLines(fixedValues, walls, lane, edgesOf) {
    const values = new Set(fixedValues);
    const offset = LANE_SPACING * (lane + 1);
    for (const wall of walls) {
      const [low, high] = edgesOf(wall.rect);
      values.add(low - offset);
      values.add(high + offset);
    }
    // A couple of tracks stepped off the endpoints give the search somewhere to
    // go when its first choice is taken. Kept to two: the grid is the product of
    // both axes, so every extra line per axis costs quadratically, and on a real
    // 50-box diagram a generous grid blew past the budget and the search bailed
    // out on every single link.
    for (const fixed of fixedValues) {
      values.add(fixed + offset);
      values.add(fixed - offset);
    }
    return dedupeSortedValues([...values].sort((a, b) => a - b));
  }

  /**
   * Drops grid lines closer together than a pixel. Two lines a fraction apart
   * offer the search no route it does not already have, but each one multiplies
   * the cell count against the other axis.
   */
  function dedupeSortedValues(sorted) {
    const kept = [];
    for (const value of sorted) {
      if (kept.length === 0 || value - kept[kept.length - 1] >= 1) {
        kept.push(value);
      }
    }
    return kept;
  }

  /**
   * Records every unit of a routed polyline as occupied.
   *
   * <p>The search sees the grid's own short segments, while the finished path has
   * had its collinear midpoints dropped into long ones. Reserving only the long
   * ones would never match a grid step, so the penalty would never fire — the
   * bug that let parallel links keep collapsing together. Sampling the polyline
   * at a fixed pitch makes the two views agree.
   */
  function reserveTraversedSegments(points, reserved) {
    for (let i = 0; i + 1 < points.length; i++) {
      forEachOccupiedCell(points[i], points[i + 1], (cell) => reserved.add(cell));
    }
  }

  /**
   * Calls back with a key per unit of ground a segment covers.
   *
   * <p>Keyed as (axis, line, cell-index-along-the-line) rather than as a whole
   * segment: the search walks grid lines whose coordinates never coincide with a
   * finished polyline's, so comparing segments end-to-end never matched and the
   * shared-segment penalty silently never fired.
   */
  function forEachOccupiedCell(from, to, visit) {
    const vertical = Math.round(from.x) === Math.round(to.x);
    const line = vertical ? Math.round(from.x) : Math.round(from.y);
    const low = vertical ? Math.min(from.y, to.y) : Math.min(from.x, to.x);
    const high = vertical ? Math.max(from.y, to.y) : Math.max(from.x, to.x);
    const axis = vertical ? "V" : "H";
    const firstCell = Math.floor(low / RESERVATION_PITCH);
    const lastCell = Math.floor(high / RESERVATION_PITCH);
    for (let cell = firstCell; cell <= lastCell; cell++) {
      visit(axis + line + ":" + cell);
    }
  }

  /**
   * Whether a candidate step would run along ground another link already holds.
   *
   * <p>Sampled the same way reservations are recorded, so a grid step of any
   * length is compared against the same pitch — an exact key match would miss
   * every step whose endpoints do not happen to coincide with the earlier link's.
   */
  function overlapsReserved(from, to, reserved) {
    if (reserved.size === 0) {
      return false;
    }
    let occupied = false;
    forEachOccupiedCell(from, to, (cell) => {
      if (reserved.has(cell)) {
        occupied = true;
      }
    });
    return occupied;
  }

  /**
   * Collapses parallel links into one per class pair wherever an end sits on a
   * collapsed box's header.
   *
   * <p>Once a box is folded, every link into it lands on the same header point.
   * Drawing ten of them stacks ten arrowheads on one spot — visual noise that
   * says nothing the single line does not. Between two open boxes each call
   * still gets its own line, because there the rows they join are distinct.
   */
  function mergeLinksIntoCollapsedBoxes(links) {
    const merged = new Map();
    const kept = [];
    for (const link of links) {
      const collapsedEnd = link.sourcePosition.atHeader || link.targetPosition.atHeader;
      if (!collapsedEnd) {
        kept.push(link);
        continue;
      }
      const pairKey = link.sourcePosition.classId + "=>" + link.targetPosition.classId;
      const existing = merged.get(pairKey);
      if (!existing) {
        merged.set(pairKey, link);
        kept.push(link);
        continue;
      }
      // A dashed private call folded together with a solid public one reads as
      // the weaker claim, so the merged line keeps the solid style.
      if (link.style === LINK_STYLE.SOLID) {
        existing.style = LINK_STYLE.SOLID;
      }
    }
    return kept;
  }

  /** Segment key used both to reserve a segment and to detect reuse of one. */
  function segmentKey(a, b) {
    const ends = [[Math.round(a.x), Math.round(a.y)], [Math.round(b.x), Math.round(b.y)]]
        .sort((left, right) => left[0] - right[0] || left[1] - right[1]);
    return ends.map((point) => point.join(",")).join("|");
  }

  /** A binary min-heap, keyed by the supplied cost function. */
  class MinHeap {
    constructor(costOf) {
      this.costOf = costOf;
      this.items = [];
    }

    get size() {
      return this.items.length;
    }

    push(item) {
      this.items.push(item);
      let index = this.items.length - 1;
      while (index > 0) {
        const parent = (index - 1) >> 1;
        if (this.costOf(this.items[parent]) <= this.costOf(this.items[index])) {
          break;
        }
        this.swap(parent, index);
        index = parent;
      }
    }

    pop() {
      const top = this.items[0];
      const last = this.items.pop();
      if (this.items.length > 0) {
        this.items[0] = last;
        this.sinkDown(0);
      }
      return top;
    }

    sinkDown(start) {
      let index = start;
      for (;;) {
        const left = index * 2 + 1;
        const right = left + 1;
        let smallest = index;
        if (left < this.items.length && this.costOf(this.items[left]) < this.costOf(this.items[smallest])) {
          smallest = left;
        }
        if (right < this.items.length && this.costOf(this.items[right]) < this.costOf(this.items[smallest])) {
          smallest = right;
        }
        if (smallest === index) {
          return;
        }
        this.swap(smallest, index);
        index = smallest;
      }
    }

    swap(left, right) {
      const held = this.items[left];
      this.items[left] = this.items[right];
      this.items[right] = held;
    }
  }

  /** Least-cost walk over the grid. Returns `{points, cost}` or `null`. */
  function dijkstraGrid(start, end, xs, ys, walls, reserved, blocked) {
    const startXi = xs.indexOf(start.x);
    const startYi = ys.indexOf(start.y);
    const endXi = xs.indexOf(end.x);
    const endYi = ys.indexOf(end.y);
    if (startXi < 0 || startYi < 0 || endXi < 0 || endYi < 0) {
      return null;
    }

    const key = (xi, yi) => xi * ys.length + yi;
    const best = new Map([[key(startXi, startYi), 0]]);
    const cameFrom = new Map([[key(startXi, startYi), null]]);
    // A real heap, not a re-sorted array: on a 50-box diagram sorting the
    // frontier every iteration cost seconds per render.
    const frontier = new MinHeap((node) => node.cost);
    frontier.push({ xi: startXi, yi: startYi, cost: 0, horizontal: null });

    while (frontier.size > 0) {
      const current = frontier.pop();
      if (current.xi === endXi && current.yi === endYi) {
        return { points: tracePath(cameFrom, current, xs, ys, key), cost: current.cost };
      }
      if (current.cost > (best.get(key(current.xi, current.yi)) ?? Infinity)) {
        continue;
      }
      for (const step of gridSteps(current, xs, ys, walls, reserved, blocked)) {
        const stepKey = key(step.xi, step.yi);
        if (step.cost < (best.get(stepKey) ?? Infinity)) {
          best.set(stepKey, step.cost);
          cameFrom.set(stepKey, current);
          frontier.push(step);
        }
      }
    }
    return null;
  }

  /** The four axis-aligned steps whose segment clears every wall, with their costs. */
  function gridSteps(node, xs, ys, walls, reserved, blocked) {
    const candidates = [
      { xi: node.xi + 1, yi: node.yi }, { xi: node.xi - 1, yi: node.yi },
      { xi: node.xi, yi: node.yi + 1 }, { xi: node.xi, yi: node.yi - 1 }
    ];
    const from = { x: xs[node.xi], y: ys[node.yi] };
    const steps = [];
    for (const candidate of candidates) {
      if (candidate.xi < 0 || candidate.xi >= xs.length || candidate.yi < 0 || candidate.yi >= ys.length) {
        continue;
      }
      const to = { x: xs[candidate.xi], y: ys[candidate.yi] };
      // Each grid edge is tested against every wall, and the search revisits
      // edges constantly, so the verdict is memoised per edge.
      const edgeKey = node.xi + "," + node.yi + ">" + candidate.xi + "," + candidate.yi;
      let isBlocked = blocked.get(edgeKey);
      if (isBlocked === undefined) {
        isBlocked = walls.some((wall) => segmentCrossesRect(from, to, wall.rect));
        blocked.set(edgeKey, isBlocked);
      }
      if (isBlocked) {
        continue;
      }
      const horizontal = candidate.yi === node.yi;
      let cost = node.cost + Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
      if (node.horizontal !== null && node.horizontal !== horizontal) {
        cost += TURN_PENALTY;
      }
      if (overlapsReserved(from, to, reserved)) {
        cost += SHARED_SEGMENT_PENALTY;
      }
      // A line grazing a border reads as if drawn on it. Charged rather than
      // forbidden: in a tight gap the only way through really is close to a
      // box, and refusing it outright pushed paths back through boxes.
      if (walls.some((wall) => segmentGrazesRect(from, to, wall.rect))) {
        cost += GRAZE_PENALTY;
      }
      steps.push({ xi: candidate.xi, yi: candidate.yi, cost, horizontal });
    }
    return steps;
  }

  /** Walks parent links back to the start. */
  function tracePath(cameFrom, endNode, xs, ys, key) {
    const points = [];
    let cursor = endNode;
    while (cursor) {
      points.unshift({ x: xs[cursor.xi], y: ys[cursor.yi] });
      cursor = cameFrom.get(key(cursor.xi, cursor.yi));
    }
    return points;
  }

  /**
   * An endpoint box as the parts of itself above and below its attachment row,
   * so a path can reach that row from either side without crossing the box.
   */
  function splitAroundRow(rect, rowY) {
    const parts = [];
    const gap = LANE_SPACING / 2;
    const aboveHeight = rowY - gap - rect.y;
    if (aboveHeight > 0) {
      parts.push({ rect: { x: rect.x, y: rect.y, width: rect.width, height: aboveHeight } });
    }
    const belowY = rowY + gap;
    const belowHeight = rect.y + rect.height - belowY;
    if (belowHeight > 0) {
      parts.push({ rect: { x: rect.x, y: belowY, width: rect.width, height: belowHeight } });
    }
    return parts;
  }

  /**
   * Whether a mousedown target should begin a box drag.
   *
   * <p>Only the box outline itself — everything interactive inside it (the class
   * name, the expander, the fold control, the member rows) keeps its click.
   */
  function isDragHandle(target) {
    if (!target || typeof target.getAttribute !== "function") {
      return true;
    }
    const classes = (target.getAttribute("class") || "").split(" ");
    return classes.includes(BOX_RECT_CLASS) || classes.includes(BOX_HEADER_FILL_CLASS);
  }

  /** Whether a segment runs closer to a rectangle than {@link BOX_CLEARANCE} without entering it. */
  function segmentGrazesRect(a, b, rect) {
    const inflated = {
      x: rect.x - BOX_CLEARANCE,
      y: rect.y - BOX_CLEARANCE,
      width: rect.width + BOX_CLEARANCE * 2,
      height: rect.height + BOX_CLEARANCE * 2
    };
    return segmentCrossesRect(a, b, inflated);
  }

  /** Whether an axis-aligned segment penetrates a rectangle's interior. */
  function segmentCrossesRect(a, b, rect) {
    const pad = 0.5;
    return Math.min(a.x, b.x) < rect.x + rect.width - pad && Math.max(a.x, b.x) > rect.x + pad
        && Math.min(a.y, b.y) < rect.y + rect.height - pad && Math.max(a.y, b.y) > rect.y + pad;
  }

  /** Collapses runs of points on one straight line into their two ends. */
  function dropCollinearPoints(points) {
    return points.filter((point, index) => {
      if (index === 0 || index === points.length - 1) {
        return true;
      }
      const previous = points[index - 1];
      const next = points[index + 1];
      return !((previous.x === point.x && point.x === next.x) || (previous.y === point.y && point.y === next.y));
    });
  }

  /**
   * The original fixed-shape route, kept as the fallback for a link the grid
   * search cannot solve (a degenerate layout, or a grid over budget).
   */
  function routeByTemplate(link, obstacles) {
    const sourceY = link.from.rowY;
    const targetY = link.to.rowY;
    const sourceExitX = link.from.rect.x + link.from.rect.width;
    // A link running to a box in the same column (or behind) approaches from the
    // right, the side the corridor is on. Entering on the left would mean
    // crossing the whole target box to reach its own edge.
    const targetLeftX = link.to.rect.x;
    const targetRightX = link.to.rect.x + link.to.rect.width;

    // The corridor must clear BOTH endpoint boxes, not just the ones in
    // between. When the target sits in the same column as the source — or to
    // its left — a corridor placed between their edges lands *inside* them, and
    // since neither endpoint is in `obstacles`, nothing corrects it: the line
    // then sweeps back across its own boxes' interiors. Treating both endpoints
    // as walls makes forward, same-column, and backward links one case.
    const corridorObstacles = obstacles.concat([{ rect: link.from.rect }, { rect: link.to.rect }]);
    const laneX = laneCorridorX(sourceExitX, link.lane, sourceY, targetY, corridorObstacles);

    // Which side of the target the corridor ends up on decides the entry side.
    // Approaching from the left is only possible while the corridor is still
    // left of the target: obstacle clearance can push it past, and entering the
    // left edge from beyond the right one would cut clean through the box.
    const entersFromRight = laneX >= targetLeftX;
    const targetEntryX = entersFromRight ? targetRightX : targetLeftX;

    // Stubs step out from each row toward the corridor, clamped so a high lane
    // index cannot push one past the corridor and send the leg backwards —
    // unclamped, lane >= 7 put a stub inside the next column.
    const sourceStubX = Math.min(sourceExitX + LANE_SPACING * (link.lane + 1), laneX);
    const targetStubX = entersFromRight
        ? Math.max(targetEntryX + LANE_SPACING * (link.lane + 1), laneX)
        : Math.min(Math.max(targetEntryX - LANE_SPACING * (link.lane + 1), sourceExitX), laneX);

    const sourceDetourY = rowLegDetourY(sourceStubX, sourceY, laneX, targetY, obstacles);
    const targetDetourY = rowLegDetourY(targetStubX, targetY, laneX, sourceY, obstacles);
    const points = [
      { x: sourceExitX, y: sourceY },
      { x: sourceStubX, y: sourceY },
      { x: sourceStubX, y: sourceDetourY },
      { x: laneX, y: sourceDetourY },
      { x: laneX, y: targetDetourY },
      { x: targetStubX, y: targetDetourY },
      { x: targetStubX, y: targetY },
      { x: targetEntryX, y: targetY }
    ];
    return dedupeConsecutivePoints(points);
  }

  /**
   * The y at which a row leg's lane-offset stub (spec 007 §6.4.4) may safely
   * turn onto the lane (spec 007 §6.4.3): `rowY` itself, unless some obstacle
   * sitting in a column between the source and target straddles `rowY` and
   * would block a horizontal sweep from the stub to `laneX` — a case the
   * vertical-run clearance in {@link #laneCorridorX} does not protect
   * against, since that only clears the lane's own x, not a row-height sweep
   * that starts short of it. When blocked, the leg detours to the near edge
   * of the blocking obstacle that still lies within the `[rowY, otherRowY]`
   * band the lane's vertical run is already known clear across, so the
   * detour never introduces a *new* uncleared x/y combination.
   */
  function rowLegDetourY(stubX, rowY, laneX, otherRowY, obstacles) {
    const legMinX = Math.min(stubX, laneX);
    const legMaxX = Math.max(stubX, laneX);
    const blocking = obstacles.filter((obstacle) => obstacleBlocksRowLeg(obstacle, rowY, legMinX, legMaxX));
    if (blocking.length === 0) {
      return rowY;
    }
    const towardOther = otherRowY >= rowY;
    const edges = blocking.map((obstacle) => (towardOther ? obstacle.rect.y + obstacle.rect.height : obstacle.rect.y));
    return towardOther ? Math.max(...edges) : Math.min(...edges);
  }

  /** Orthogonal routing never needs two consecutive identical points; collapses the (common) zero-length leg. */
  function dedupeConsecutivePoints(points) {
    return points.filter((point, index) => index === 0
        || point.x !== points[index - 1].x || point.y !== points[index - 1].y);
  }

  /**
   * Whether an obstacle's rectangle sits on the horizontal path a link's
   * leading or trailing leg would sweep at a fixed row `y`, between the box
   * edge and the lane (spec 007 §6.4.3) — the case the simple lane-clearance
   * check on the vertical run alone misses: an obstacle in a column between
   * the source and target, straddling the row's own y.
   */
  function obstacleBlocksRowLeg(obstacle, rowY, legMinX, legMaxX) {
    const rect = obstacle.rect;
    const rowIsInsideObstacle = rowY > rect.y && rowY < rect.y + rect.height;
    const obstacleOverlapsLeg = rect.x + rect.width > legMinX && rect.x < legMaxX;
    return rowIsInsideObstacle && obstacleOverlapsLeg;
  }

  /**
   * The lane's x position, nudged right of any obstacle the vertical run
   * would otherwise cross (spec 007 §6.4.3). The lane's own offset
   * (`lane * LANE_SPACING`) is re-applied on top of whatever obstacle
   * clearance was needed, rather than every cleared lane collapsing onto the
   * same fixed "just past the obstacle" x — two links that both have to dodge
   * the same box still end up on two distinct x's, so a shared corridor with
   * several boxes to route around never merges two lanes into one (AC11).
   */
  function laneCorridorX(sourceExitX, lane, sourceY, targetY, obstacles) {
    // Anchored at the source's right edge, never at min(source, target): for a
    // same-column or backward link the target's left edge is at or behind the
    // source's, so a min-based base would place the corridor inside the boxes.
    const laneOffset = LANE_SPACING * (lane + 1);
    const clearanceFloor = obstacleClearanceFloor(sourceExitX, sourceY, targetY, obstacles);
    return Math.max(sourceExitX, clearanceFloor) + laneOffset;
  }

  /** The furthest right edge, among every obstacle the vertical run would otherwise cross, that lanes must clear. */
  function obstacleClearanceFloor(baseX, sourceY, targetY, obstacles) {
    const minY = Math.min(sourceY, targetY);
    const maxY = Math.max(sourceY, targetY);
    let floor = baseX;
    for (const obstacle of obstacles) {
      const rect = obstacle.rect;
      const verticalRunOverlapsObstacle = maxY > rect.y && minY < rect.y + rect.height;
      const obstacleIsToTheRight = rect.x + rect.width > baseX;
      if (verticalRunOverlapsObstacle && obstacleIsToTheRight) {
        floor = Math.max(floor, rect.x + rect.width);
      }
    }
    return floor;
  }

  /**
   * A small loop leaving and re-entering the same box's right edge (spec 007
   * §6.4, self-links). A box with several self-links (a constructor calling
   * several of its own builder methods, say) stacks their loops outward by
   * lane, so the loop width is clamped against whatever obstacle it would
   * otherwise reach into — a following column's box, most often — rather than
   * growing unbounded with the lane index (spec 007 §6.4.3).
   */
  function routeSelfLink(link, obstacles) {
    const exitX = link.from.rect.x + link.from.rect.width;
    const sourceY = link.from.rowY;
    const targetY = link.to.rowY;
    const maxAllowedX = selfLinkCeilingX(exitX, sourceY, targetY, obstacles || []);
    const loopX = selfLinkLoopX(exitX, link.lane, maxAllowedX);
    return [
      { x: exitX, y: sourceY },
      { x: loopX, y: sourceY },
      { x: loopX, y: targetY },
      { x: exitX, y: targetY }
    ];
  }

  /**
   * The furthest x a self-link's loop may reach, given every obstacle whose
   * row range it would otherwise cross (spec 007 §6.4.3) — a following
   * column's box, most often. {@code Number.POSITIVE_INFINITY} when nothing
   * constrains it.
   */
  function selfLinkCeilingX(exitX, sourceY, targetY, obstacles) {
    let ceiling = Number.POSITIVE_INFINITY;
    const minY = Math.min(sourceY, targetY);
    const maxY = Math.max(sourceY, targetY);
    for (const obstacle of obstacles) {
      const rowRangeOverlapsObstacle = maxY > obstacle.rect.y && minY < obstacle.rect.y + obstacle.rect.height;
      if (rowRangeOverlapsObstacle && obstacle.rect.x > exitX) {
        ceiling = Math.min(ceiling, obstacle.rect.x - LANE_SPACING);
      }
    }
    return ceiling;
  }

  /**
   * A self-link's loop x for its lane, spread out from `exitX` by
   * {@link #SELF_LINK_LOOP_WIDTH} plus a per-lane step (spec 007 §6.4.4) —
   * distinct lanes must stay distinct even when a ceiling forces the step to
   * shrink, so the step is compressed to fit the available room rather than
   * every over-budget lane collapsing onto the same clamped x (which would
   * violate "no two links share a segment", AC11). {@code lane * budget /
   * (lane + 1)} is strictly increasing in `lane` for a fixed positive
   * `budget`, so distinctness holds for any number of self-links sharing a box.
   */
  function selfLinkLoopX(exitX, lane, maxAllowedX) {
    const desiredLoopX = exitX + SELF_LINK_LOOP_WIDTH + lane * LANE_SPACING;
    if (desiredLoopX <= maxAllowedX) {
      return desiredLoopX;
    }
    const budget = Math.max(maxAllowedX - exitX - SELF_LINK_LOOP_WIDTH, LANE_SPACING);
    return exitX + SELF_LINK_LOOP_WIDTH + (budget * lane) / (lane + 1);
  }

  /** Renders a routed polyline (array of {@code {x, y}}) as an SVG path `d` attribute. */
  function polylinePath(points) {
    return points.map((point, index) => (index === 0 ? "M" : "L") + point.x + "," + point.y).join(" ");
  }

  /**
   * Assigns each link crossing one inter-column gap its own lane index (spec
   * 007 §6.4.1, §6.4.4): links are ordered deterministically by their
   * endpoints so the same input always yields the same assignment, then
   * numbered 0..n-1 — a distinct lane per link is what keeps two parallel
   * relationships from ever sharing a routed segment.
   *
   * @param links every link crossing the same gap, each carrying a stable {@code id}
   * @return {@code Map<linkId, laneIndex>}
   */
  function allocateLanes(links) {
    const ordered = [...links].sort((a, b) => {
      const bySource = a.source.localeCompare(b.source);
      if (bySource !== 0) {
        return bySource;
      }
      const byTarget = a.target.localeCompare(b.target);
      return byTarget !== 0 ? byTarget : a.id.localeCompare(b.id);
    });
    const lanes = new Map();
    ordered.forEach((link, index) => lanes.set(link.id, index));
    return lanes;
  }

  /**
   * Groups links by the inter-column gap they cross and allocates lanes
   * within each group independently (spec 007 §6.4.1) — two links crossing
   * different gaps may legitimately share a lane index, since they never
   * share a corridor; two links crossing the same gap never do (§6.4.4).
   *
   * @param links every link currently drawn, each carrying a stable {@code id}
   * @param gapKeyOf maps a link to the key identifying the gap it crosses
   * @return {@code Map<linkId, laneIndex>}
   */
  function assignLanesByGap(links, gapKeyOf) {
    const byGap = groupBy(links, gapKeyOf);
    const lanes = new Map();
    for (const linksInGap of byGap.values()) {
      for (const [linkId, lane] of allocateLanes(linksInGap)) {
        lanes.set(linkId, lane);
      }
    }
    return lanes;
  }

  // ---------------------------------------------------------------------
  // Side panel data (spec 007 §4.2): class-granular `calls`/`called by` —
  // deduplicated classes, never a flat method list — projected from the same
  // `edges` list the diagram itself reads, so no second source of truth
  // exists for "what calls what".
  // ---------------------------------------------------------------------

  /** The distinct classes reached by a set of edges, via each edge's target/source method's owning class. */
  function distinctClassIds(index, edges, methodIdOf) {
    const classIds = new Set();
    for (const edge of edges) {
      const method = index.method(methodIdOf(edge));
      if (method) {
        classIds.add(method.classId);
      }
    }
    return [...classIds];
  }

  /**
   * Projects a class into what its side panel shows when its name is clicked
   * (spec 007 §4.2): class-granular `calls`/`called by`, then the full
   * verbatim source, plus the methods it declares for the compartment lists.
   *
   * @return {@code null} when the class id is unknown
   */
  function buildClassPanelData(index, classId) {
    const owningClass = index.classOf(classId);
    if (!owningClass) {
      return null;
    }
    const module = index.module(owningClass.moduleId);
    const methods = index.methodsOfClass(classId);
    const methodIds = new Set(methods.map((m) => m.id));
    const outgoingEdges = methods.flatMap((m) => index.outgoing(m.id)).filter((e) => index.isDrawableCallTarget(e));
    const incomingEdges = methods.flatMap((m) => index.incoming(m.id)).filter((e) => !methodIds.has(e.from));
    return {
      classId,
      simpleName: owningClass.simpleName,
      fqn: owningClass.fqn,
      file: owningClass.file,
      layer: owningClass.layer,
      moduleName: module ? module.name : owningClass.moduleId,
      javadoc: owningClass.javadoc,
      source: owningClass.source,
      methods,
      callsClassIds: distinctClassIds(index, outgoingEdges, (e) => e.to).filter((id) => id !== classId),
      calledByClassIds: distinctClassIds(index, incomingEdges, (e) => e.from).filter((id) => id !== classId)
    };
  }

  /**
   * Projects a method into what its side panel shows when its row name is
   * clicked (spec 007 §4.2): the method's real source, plus the distinct
   * classes that call it.
   *
   * @return {@code null} when the method id is unknown
   */
  function buildMethodPanelData(index, methodId) {
    const method = index.method(methodId);
    if (!method) {
      return null;
    }
    const owner = index.classOf(method.classId);
    const module = owner ? index.module(owner.moduleId) : null;
    const incomingEdges = index.incoming(methodId).filter((e) => e.from !== methodId);
    return {
      methodId,
      name: method.name,
      signature: method.signature,
      file: method.file,
      lineStart: method.lineStart,
      lineEnd: method.lineEnd,
      javadoc: method.javadoc,
      source: method.source,
      visibility: method.visibility,
      layer: owner ? owner.layer : null,
      moduleName: module ? module.name : null,
      calledByClassIds: distinctClassIds(index, incomingEdges, (e) => e.from).filter((id) => id !== method.classId)
    };
  }

  // ---------------------------------------------------------------------
  // Entry-point picker filtering (spec §7, kept from feature/6): search,
  // layer, and module narrow which entry points a reader can open — the
  // diagram itself only ever contains what was actually expanded.
  // ---------------------------------------------------------------------

  /**
   * Narrows the entry-point list by search term (matches the pill label or
   * the declaring class's simple name), layer (of the declaring class), and
   * owning module — every criterion is optional and they combine with AND.
   *
   * @param entryPoints every entry point in the index
   * @param index the {@link CodemapIndex}
   * @param criteria {@code {searchTerm, layer, moduleId}}, each optional
   */
  function filterEntryPoints(entryPoints, index, criteria) {
    const searchTerm = (criteria.searchTerm || "").toLowerCase();
    return entryPoints.filter((entryPoint) => {
      const method = index.method(entryPoint.methodId);
      const owner = method && index.classOf(method.classId);
      if (criteria.moduleId && entryPoint.moduleId !== criteria.moduleId) {
        return false;
      }
      if (criteria.layer && (!owner || owner.layer !== criteria.layer)) {
        return false;
      }
      if (searchTerm && !entryPointMatchesSearch(entryPoint, owner, searchTerm)) {
        return false;
      }
      return true;
    });
  }

  function entryPointMatchesSearch(entryPoint, owner, searchTerm) {
    if (entryPoint.label.toLowerCase().includes(searchTerm)) {
      return true;
    }
    return owner ? owner.simpleName.toLowerCase().includes(searchTerm) : false;
  }

  // ---------------------------------------------------------------------
  // Pixel layout (spec 007 §6.3): applies {@link assignColumns} and
  // {@link orderColumnByBarycentre} to a live {@link DiagramState}, sizing
  // each box by its compartment row count and keeping positions stable
  // across re-layout — a box only moves if its own column grows and only
  // along the cross axis.
  // ---------------------------------------------------------------------

  const BOX_WIDTH = 220;
  /** A box may grow this wide to fit its rows before labels get shortened instead. */
  const BOX_MAX_WIDTH = 420;
  /** Approximate advance width of one character at the row font size. */
  const CHAR_WIDTH = 6.2;
  /** Row text starts here; the expander needs this much clear on the right. */
  const ROW_TEXT_X = 12;
  const ROW_EXPANDER_RESERVE = 30;
  /** Room the header keeps for its status glyph, fold control and expander. */
  const HEADER_CONTROLS_RESERVE = 96;
  const ELLIPSIS = "…";
  const COLUMN_GAP = 90;
  const ROW_HEIGHT = 20;
  const HEADER_HEIGHT = 26;
  const COMPARTMENT_RULE_HEIGHT = 6;
  const BOX_VERTICAL_GAP = 24;
  const PILL_WIDTH = 160;
  const PILL_HEIGHT = 34;
  /** Baseline drop from a row's top edge to its text, so glyph and label align. */
  const ROW_LABEL_BASELINE_OFFSET = 14;

  /** The full, unshortened text of one member row. */
  function fullMemberRowLabel(method) {
    return visibilityMarker(method.visibility) + " " + method.signature;
  }

  /** How many characters fit in a row of a box `boxWidth` wide. */
  function rowCharacterBudget(boxWidth) {
    return Math.max(8, Math.floor((boxWidth - ROW_TEXT_X - ROW_EXPANDER_RESERVE) / CHAR_WIDTH));
  }

  /**
   * The row text as drawn: shortened to fit the box.
   *
   * <p>Real signatures run long — a handler constructor taking six use-cases is
   * 164 characters, and over half of Kairos's rows exceed a fixed 220px box. The
   * parameter list is where the length lives and is the least load-bearing part
   * of the row, so it collapses first; the method name always survives. The
   * untruncated signature stays one click away in the side panel.
   */
  function memberRowLabel(method, boxWidth) {
    const full = fullMemberRowLabel(method);
    const budget = rowCharacterBudget(boxWidth);
    if (full.length <= budget) {
      return full;
    }
    const open = full.indexOf("(");
    const close = full.lastIndexOf(")");
    if (open > 0 && close > open) {
      // Collapse the parameter list first — it carries the length and is the
      // least load-bearing part of the row. `tail` keeps the return type, which
      // sits after ")" and must be counted against the budget too.
      const head = full.slice(0, open + 1);
      const tail = full.slice(close);
      const inner = budget - head.length - tail.length;
      if (inner >= ELLIPSIS.length) {
        const params = full.slice(open + 1, close);
        return head + params.slice(0, inner - ELLIPSIS.length) + ELLIPSIS + tail;
      }
      // Even an empty parameter list does not fit: drop the return type too,
      // then hard-truncate. The method name is what must survive.
      const collapsed = head + ELLIPSIS + ")";
      if (collapsed.length <= budget) {
        return collapsed;
      }
    }
    return full.slice(0, Math.max(1, budget - ELLIPSIS.length)) + ELLIPSIS;
  }

  /**
   * The width a box needs for its widest row, capped at {@link BOX_MAX_WIDTH}.
   * Sizing to content keeps short boxes compact while stopping long signatures
   * from spilling across the canvas and over the expander.
   */
  function boxWidthFor(compartments, className) {
    const rows = [...compartments.constructors, ...compartments.publicMethods, ...compartments.revealedPrivateMethods];
    const longest = rows.reduce((widest, method) => Math.max(widest, fullMemberRowLabel(method).length), 0);
    const needed = ROW_TEXT_X + longest * CHAR_WIDTH + ROW_EXPANDER_RESERVE;
    // The header has to fit too: the class name plus room for its three
    // controls. A collapsed box has no rows at all, so this is the only thing
    // keeping its name from running under the controls.
    const headerNeeded = ROW_TEXT_X + (className || "").length * CHAR_WIDTH + HEADER_CONTROLS_RESERVE;
    return Math.round(Math.min(BOX_MAX_WIDTH, Math.max(BOX_WIDTH, needed, headerNeeded)));
  }

  /** The pixel height a box needs for its current compartments (spec 007 §2.2). */
  function boxHeight(compartments) {
    const rows = compartments.constructors.length + compartments.publicMethods.length
        + compartments.revealedPrivateMethods.length;
    const rules = [compartments.constructors, compartments.publicMethods, compartments.revealedPrivateMethods]
        .filter((rows) => rows.length > 0).length;
    return HEADER_HEIGHT + rows * ROW_HEIGHT + rules * COMPARTMENT_RULE_HEIGHT;
  }

  /**
   * Lays out every box currently in `diagram` into columns by call depth
   * from `rootPillId` (spec 007 §6.3), ordering each column by barycentre,
   * and stacking boxes top-down within a column with a fixed gap.
   *
   * @param diagram the live {@link DiagramState}
   * @param index the {@link CodemapIndex}, for compartment sizing
   * @param rootPillId the entry point id, column 0
   * @return {@code {boxPositions: Map<classId, {rect, column}>}}
   */
  function layoutDiagram(diagram, index, rootPillId, collapsedClassIds) {
    const collapsed = collapsedClassIds || new Set();
    const classIds = [...diagram.boxes.keys()];
    const links = [...pillLinksFor(index, rootPillId), ...diagramLinksFor(diagram, index)];
    const columns = assignColumns([rootPillId, ...classIds], links, rootPillId);

    const byColumn = groupBy(classIds, (classId) => columns.get(classId));
    const boxPositions = new Map();
    const parentPositions = { [rootPillId]: 0 };
    const maxColumn = Math.max(0, ...classIds.map((id) => columns.get(id)));
    // Starts at 0, not 1: a box no link reaches falls back to column 0, and
    // skipping that column would leave it in the model but never drawn —
    // present, unreachable, and impossible to collapse.
    let columnX = ROOT_MARGIN_X + PILL_WIDTH + COLUMN_GAP;
    for (let column = 0; column <= maxColumn; column++) {
      const boxesInColumn = byColumn.get(column) || [];
      if (boxesInColumn.length === 0) {
        continue;
      }
      const ordered = orderColumnByBarycentre(boxesInColumn, links, parentPositions);
      columnX = placeColumn(ordered, column, columnX, diagram, index, boxPositions, parentPositions, collapsed);
    }
    const pillRect = { x: ROOT_MARGIN_X, y: ROOT_MARGIN_Y, width: PILL_WIDTH, height: PILL_HEIGHT };
    return { boxPositions, pillRect };
  }

  /**
   * Shifts each box by whatever the reader has dragged it, leaving the computed
   * layout itself untouched.
   *
   * <p>Applied after layout rather than baked into it, so the columns keep their
   * own logic — a dragged box does not push its neighbours around, and clearing
   * the offset puts it straight back where the layout wanted it.
   */
  function applyBoxOffsets(layout, offsets) {
    if (!offsets || offsets.size === 0) {
      return layout;
    }
    const moved = new Map();
    for (const [classId, position] of layout.boxPositions) {
      const offset = offsets.get(classId);
      moved.set(classId, offset
          ? { ...position, rect: { ...position.rect, x: position.rect.x + offset.x, y: position.rect.y + offset.y } }
          : position);
    }
    return { ...layout, boxPositions: separateOverlaps(moved, offsets) };
  }

  /**
   * Nudges un-dragged boxes down until nothing overlaps.
   *
   * <p>A dragged box is where the reader put it, so it never yields; everything
   * else gives way. Without this a drop on top of a neighbour left two boxes
   * sharing the same space and the router — which treats boxes as walls — was
   * asked for paths through solid ground, so links ran along the seam.
   */
  function separateOverlaps(positions, offsets) {
    const entries = [...positions.entries()];
    // Dragged boxes are resolved first and never moved, so they win any contest.
    entries.sort(([leftId], [rightId]) =>
        (offsets.has(rightId) ? 1 : 0) - (offsets.has(leftId) ? 1 : 0));

    const settled = [];
    const result = new Map();
    for (const [classId, position] of entries) {
      let rect = position.rect;
      if (!offsets.has(classId)) {
        for (let attempt = 0; attempt < OVERLAP_RESOLUTION_ATTEMPTS; attempt++) {
          const clash = settled.find((other) => rectsOverlap(rect, other));
          if (!clash) {
            break;
          }
          rect = { ...rect, y: clash.y + clash.height + BOX_VERTICAL_GAP };
        }
      }
      settled.push(rect);
      result.set(classId, { ...position, rect });
    }
    return result;
  }

  function rectsOverlap(a, b) {
    return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
  }

  /** The synthetic pill -> declaring-class link that seeds column 0 -> column 1 (spec 007 §4.1). */
  function pillLinksFor(index, rootPillId) {
    const entryPoint = index.data.entryPoints.find((candidate) => candidate.id === rootPillId);
    const method = entryPoint && index.method(entryPoint.methodId);
    return method ? [{ source: rootPillId, target: method.classId }] : [];
  }

  /** Every link between currently-drawn boxes, derived from resolved edges between their methods. */
  function diagramLinksFor(diagram, index) {
    const classIds = new Set(diagram.boxes.keys());
    const links = [];
    for (const classId of classIds) {
      for (const method of index.methodsOfClass(classId)) {
        for (const edge of index.outgoing(method.id)) {
          addLinkIfCollaboratorIsDrawn(index, edge, classId, classIds, links);
        }
      }
    }
    return links;
  }

  function addLinkIfCollaboratorIsDrawn(index, edge, sourceClassId, drawnClassIds, links) {
    if (!index.isDrawableCallTarget(edge)) {
      return;
    }
    const targetMethod = index.method(edge.to);
    if (targetMethod.classId !== sourceClassId && drawnClassIds.has(targetMethod.classId)) {
      links.push({ source: sourceClassId, target: targetMethod.classId });
    }
  }

  /**
   * Places one column's boxes, returning the x where the next column starts.
   *
   * <p>Boxes are sized to their content, so a column is as wide as its widest
   * box and the caller carries the running offset — a fixed stride would let a
   * wide box overlap the column beside it.
   */
  function placeColumn(orderedClassIds, column, columnX, diagram, index, boxPositions, parentPositions, collapsed) {
    let cursorY = ROOT_MARGIN_Y;
    let columnWidth = BOX_WIDTH;
    for (const classId of orderedClassIds) {
      const box = diagram.boxFor(classId);
      const isCollapsed = collapsed.has(classId);
      // A collapsed box keeps its identity and its links but gives up its rows,
      // so the reader can push a class they have already read out of the way.
      const compartments = isCollapsed
          ? EMPTY_COMPARTMENTS
          : buildCompartments(index, classId, box.revealedPrivateMethodIds);
      const height = isCollapsed ? HEADER_HEIGHT : boxHeight(compartments);
      const width = boxWidthFor(compartments, index.classOf(classId) && index.classOf(classId).simpleName);
      columnWidth = Math.max(columnWidth, width);
      const rect = { x: columnX, y: cursorY, width, height };
      boxPositions.set(classId, { rect, column, compartments });
      parentPositions[classId] = cursorY;
      cursorY += height + BOX_VERTICAL_GAP;
    }
    return columnX + columnWidth + COLUMN_GAP;
  }

  // ---------------------------------------------------------------------
  // DiagramView (spec 007 §4): the D3-facing orchestration. Owns exactly one
  // {@link DiagramController} (and therefore one {@link DiagramState}) per
  // report, translating clicks into expand/collapse calls and re-rendering
  // the SVG. Selection/side-panel state lives here since it is what the
  // click handlers and the side panel both need.
  // ---------------------------------------------------------------------

  const SELECTION_KIND = { CLASS: "CLASS", METHOD: "METHOD", ENTRY_POINT: "ENTRY_POINT" };

  class DiagramView {
    constructor(index) {
      this.index = index;
      this.controller = new DiagramController(index);
      this.svg = d3.select("#graph");
      this.viewport = this.svg.append("g").attr("class", "viewport");
      this.selection = null;
      this.underlinedMethodIds = new Set();
      /** methodId -> the parent expander path it was expanded under. */
      this.expandedMethodRows = new Map();
      /** classIds whose header expander is currently open. */
      this.expandedClassHeaders = new Set();
      /** classIds the reader has collapsed down to just their header. */
      this.collapsedClassIds = new Set();
      /** classId -> the offset the reader has dragged that box by. */
      this.boxOffsets = new Map();
      this.dragOrigin = null;
      /** The entry method's underline, which no link owns and cleanup must not drop. */
      this.entryUnderlinedMethodId = null;
      this.openPillId = null;
      this.setupZoom();
    }

    setupZoom() {
      this.zoomBehavior = d3.zoom().scaleExtent([0.2, 3]).on("zoom", (event) => {
        this.viewport.attr("transform", event.transform);
      });
      this.svg.call(this.zoomBehavior);
      this.applyTransform(d3.zoomIdentity.translate(ROOT_MARGIN_X, ROOT_MARGIN_Y));
    }

    applyTransform(transform) {
      this.viewport.attr("transform", transform);
      this.svg.call(this.zoomBehavior.transform, transform);
    }

    /**
     * Opens an entry-point pill (spec 007 §4.1): draws its class, underlines the
     * handler row.
     *
     * <p>Switching entry points clears the canvas first. The previous pill's
     * boxes are unreachable from the new root, so keeping them would leave a
     * pile of disconnected rectangles the reader cannot collapse — and they
     * would be dropped from the layout silently, since a box no link reaches
     * gets no column.
     */
    openEntryPointPill(entryPointId) {
      if (this.openPillId !== entryPointId) {
        this.resetDiagram();
      }
      const result = this.controller.openEntryPoint(entryPointId);
      if (!result) {
        return null;
      }
      this.openPillId = entryPointId;
      this.entryUnderlinedMethodId = result.underlinedMethodId;
      this.underlinedMethodIds.add(result.underlinedMethodId);
      this.render();
      return result;
    }

    /** Drops every box, expansion, and underline so a new entry point starts clean. */
    resetDiagram() {
      this.controller.diagram.clear();
      this.expandedMethodRows.clear();
      this.expandedClassHeaders.clear();
      this.collapsedClassIds.clear();
      this.boxOffsets.clear();
      this.underlinedMethodIds.clear();
      this.entryUnderlinedMethodId = null;
      this.selection = null;
    }

    /**
     * The class-header `(+)`/`(−)` expander (spec 007 §4.3).
     *
     * <p>Held on the view rather than the box: a box is destroyed and recreated
     * as paths come and go, and losing the flag with it made a reopened header
     * offer `(+)` while its collaborators were already on the canvas.
     */
    toggleClassHeader(classId) {
      if (this.expandedClassHeaders.has(classId)) {
        this.controller.collapseClassHeader(classId);
        this.expandedClassHeaders.delete(classId);
      } else {
        this.controller.expandClassHeader(classId);
        this.expandedClassHeaders.add(classId);
      }
      this.clearUnderlinesWithoutLinks();
      this.render();
    }

    /** The method-row `(+)`/`(−)` expander (spec 007 §4.3). */
    toggleMethodRow(methodId, ownerClassId, parentPath) {
      if (this.expandedMethodRows.has(methodId)) {
        this.collapseMethodRow(methodId);
      } else {
        this.expandMethodRow(methodId, ownerClassId, parentPath);
      }
      this.render();
    }

    expandMethodRow(methodId, ownerClassId, parentPath) {
      const result = this.controller.expandMethodRow(methodId, ownerClassId, parentPath);
      for (const link of result.links) {
        if (link.underlineTarget) {
          this.underlinedMethodIds.add(link.targetMethodId);
        }
      }
      this.expandedMethodRows.set(methodId, parentPath);
      return result;
    }

    /**
     * Collapses using the path the row was actually expanded under, not one
     * recomputed from the box's current `revealingPaths` — that set's order
     * shifts as paths come and go, so recomputing could target a different
     * path and leave the box unremovable by any later click.
     */
    collapseMethodRow(methodId) {
      const parentPath = this.expandedMethodRows.get(methodId);
      this.expandedMethodRows.delete(methodId);
      if (parentPath !== undefined) {
        const retired = methodRowPath(methodId, parentPath);
        this.controller.collapseMethodRow(methodId, parentPath);
        // The diagram retires the collapsed path AND everything nested beneath
        // it, so this ledger has to do the same. Dropping only the clicked row
        // left descendants marked as expanded: re-opening the outer row then
        // resurrected an expansion nobody asked for, and the stranded row's
        // next click ran collapse instead of expand.
        this.forgetRowsBeneath(retired);
      }
      this.clearUnderlinesWithoutLinks();
    }

    /** Drops ledger entries for every row expanded through `retiredPath`. */
    forgetRowsBeneath(retiredPath) {
      const nestedPrefix = retiredPath + PATH_SEPARATOR;
      for (const [rowId, rowParentPath] of [...this.expandedMethodRows]) {
        if (rowParentPath === retiredPath || rowParentPath.startsWith(nestedPrefix)) {
          this.expandedMethodRows.delete(rowId);
        }
      }
    }

    /** Drops underlines whose link no longer exists, so no row stays marked as a target. */
    clearUnderlinesWithoutLinks() {
      const stillTargeted = new Set();
      for (const link of this.renderableLinks()) {
        if (link.underlineTarget) {
          stillTargeted.add(link.targetMethodId);
        }
      }
      if (this.entryUnderlinedMethodId) {
        stillTargeted.add(this.entryUnderlinedMethodId);
      }
      this.underlinedMethodIds = new Set(
          [...this.underlinedMethodIds].filter((methodId) => stillTargeted.has(methodId)));
    }

    /** Clicking a class name (spec 007 §4.2): opens the side panel, never touches the canvas. */
    navigateToClass(classId) {
      this.selection = { kind: SELECTION_KIND.CLASS, classId };
      this.renderSidePanel();
    }

    /** Clicking a method row name (spec 007 §4.2): opens the side panel, never touches the canvas. */
    navigateToMethod(methodId) {
      this.selection = { kind: SELECTION_KIND.METHOD, methodId };
      this.renderSidePanel();
    }

    render() {
      if (!this.openPillId) {
        return;
      }
      this.lastLayout = applyBoxOffsets(
          layoutDiagram(this.controller.diagram, this.index, this.openPillId, this.collapsedClassIds),
          this.boxOffsets);
      this.drawPill(this.lastLayout);
      this.drawBoxes(this.lastLayout);
      this.drawLinks();
    }

    /** The entry-point pill itself (spec 007 §2.1, §4.1). */
    drawPill(layout) {
      const entryPoint = this.index.data.entryPoints.find((candidate) => candidate.id === this.openPillId);
      const data = entryPoint ? [entryPoint] : [];
      const selection = this.viewport.selectAll("g.entry-point-pill").data(data, (d) => d.id);
      selection.exit().remove();
      const entered = selection.enter().append("g").attr("class", "entry-point-pill");
      entered.append("rect").attr("width", PILL_WIDTH).attr("height", PILL_HEIGHT);
      entered.append("text").attr("x", 12).attr("y", PILL_HEIGHT / 2 + 4);
      const merged = entered.merge(selection);
      merged.attr("transform", "translate(" + layout.pillRect.x + "," + layout.pillRect.y + ")");
      merged.select("text").text((d) => (d.label === "" ? ROOT_PAGE_LABEL : d.label));
    }

    drawBoxes(layout) {
      this.methodRowPositions = new Map();
      const boxes = [...layout.boxPositions.entries()].map(([classId, position]) => ({
        classId,
        ...position,
        box: this.controller.diagram.boxFor(classId)
      }));
      const selection = this.viewport.selectAll("g.class-box").data(boxes, (d) => d.classId);
      selection.exit().remove();
      const entered = selection.enter().append("g");
      const merged = entered.merge(selection);
      merged.attr("transform", (d) => "translate(" + d.rect.x + "," + d.rect.y + ")");
      merged.attr("class", (d) => this.classBoxCssClasses(d).join(" "));
      merged.each((d, i, nodes) => this.renderBoxContent(nodes[i], d));
      // Bound per box AFTER its content exists, onto the outline only.
      merged.each((d, i, nodes) => this.makeBoxDraggable(nodes[i], d));
    }

    /**
     * Lets the reader drag a box wherever they want it.
     *
     * <p>The automatic layout is a starting point, not a verdict — sometimes you
     * simply want two classes side by side to compare them. The offset is
     * remembered per class and re-applied on every layout, so expanding
     * something else does not undo the arrangement, and links re-route live as
     * the box moves.
     */
    makeBoxDraggable(groupNode, datum) {
      if (!d3.drag) {
        return;
      }
      const view = this;
      // Bound to the outline rect, never the group: d3.drag calls
      // stopImmediatePropagation on mousedown, so a group-level binding
      // swallowed the clicks of every control inside the box — the fold control
      // could set (…) but never clear it again.
      d3.select(groupNode).selectAll("rect." + BOX_RECT_CLASS).call(d3.drag()
          .on("start", (event) => {
            view.dragOrigin = {
              x: event.x,
              y: event.y,
              offset: view.boxOffsets.get(datum.classId) || { x: 0, y: 0 }
            };
          })
          .on("drag", (event) => {
            if (!view.dragOrigin) {
              return;
            }
            view.boxOffsets.set(datum.classId, {
              x: view.dragOrigin.offset.x + (event.x - view.dragOrigin.x),
              y: view.dragOrigin.offset.y + (event.y - view.dragOrigin.y)
            });
            view.render();
          })
          .on("end", () => {
            view.dragOrigin = null;
          }));
    }

    classBoxCssClasses(d) {
      const owningClass = this.index.classOf(d.classId);
      return boxCssClasses(owningClass ? owningClass.status : null, this.visibleRowStatuses(d));
    }

    /** Every currently visible row's status, for the box's own strongest-status rollup (spec 007 §3). */
    visibleRowStatuses(d) {
      return [...d.compartments.constructors, ...d.compartments.publicMethods, ...d.compartments.revealedPrivateMethods]
          .map((method) => method.status);
    }

    /** Rebuilds one box's inner SVG: header, expander, and each compartment's rows. */
    renderBoxContent(groupNode, d) {
      const group = d3.select(groupNode);
      group.selectAll("*").remove();
      group.append("rect").attr("class", BOX_RECT_CLASS).attr("width", d.rect.width).attr("height", d.rect.height);
      this.renderBoxHeader(group, d);
      this.renderCompartmentRows(group, d);
    }

    /**
     * The box header (spec 007 §2.2): an optional fill for `ADDED` boxes, the
     * `«stereotype»` line above the class name, the clickable name itself,
     * the `(+)`/`(-)` expander, and a status glyph carrying the box's status
     * redundantly with colour (spec 007 §3).
     */
    renderBoxHeader(group, d) {
      const owningClass = this.index.classOf(d.classId);
      const strongest = boxStrongestStatus(owningClass ? owningClass.status : null, this.visibleRowStatuses(d));
      this.renderHeaderFill(group, d, strongest);
      group.append("text").attr("class", "box-stereotype").attr("x", 8).attr("y", 10)
          .text(stereotypeLabel(owningClass ? owningClass.layer : null));
      const header = group.append("text").attr("class", "box-header").attr("x", 8).attr("y", 22)
          .text((owningClass ? owningClass.simpleName : d.classId));
      header.on("click", () => this.navigateToClass(d.classId));
      // Laid out right-to-left from the box edge with a real gap between each
      // control. At a fixed 16px pitch the glyph, the expander and the fold
      // control overlapped, so the fold control could not reliably be clicked.
      let cursorX = d.rect.width - HEADER_CONTROL_INSET;
      this.renderHeaderControl(group, cursorX, "expander", "(+)", () => this.toggleClassHeader(d.classId));
      cursorX -= HEADER_CONTROL_PITCH;
      // Separate from the collaborator expander on purpose: one asks "what does
      // this class use", this one just gets a class out of the way. A domain
      // type with twenty accessors is noise once you have seen it.
      const foldGlyph = this.collapsedClassIds.has(d.classId) ? COLLAPSE_GLYPH_CLOSED : COLLAPSE_GLYPH_OPEN;
      this.renderHeaderControl(group, cursorX, COLLAPSE_TOGGLE_CLASS, foldGlyph,
          () => this.toggleBoxCollapsed(d.classId));
      cursorX -= HEADER_CONTROL_PITCH;
      this.renderStatusGlyph(group, d, strongest, cursorX);
    }

    /** Collapses a box to its header, or restores it (spec 007 §2.2). */
    toggleBoxCollapsed(classId) {
      if (this.collapsedClassIds.has(classId)) {
        this.collapsedClassIds.delete(classId);
      } else {
        this.collapsedClassIds.add(classId);
      }
      this.render();
    }

    /**
     * One header control: the glyph plus an invisible hit area behind it.
     *
     * <p>The glyph alone is about 16x13 — a small target to hit, and missing it
     * silently does nothing, which reads as the control being broken. The pad
     * makes the clickable area the size a pointer actually expects without
     * changing how the header looks.
     */
    renderHeaderControl(group, x, cssClass, glyph, onClick) {
      group.append("rect")
          .attr("class", HEADER_HIT_AREA_CLASS)
          .attr("x", x - HEADER_HIT_PAD_X)
          .attr("y", 16 - HEADER_HIT_HEIGHT / 2 - HEADER_HIT_BASELINE_DROP)
          .attr("width", HEADER_CONTROL_PITCH)
          .attr("height", HEADER_HIT_HEIGHT)
          .on("click", onClick);
      group.append("text").attr("class", cssClass).attr("x", x).attr("y", 16).text(glyph)
          .on("click", onClick);
    }

    /** The green header fill an `ADDED` box gets, in addition to its solid green border (spec 007 §3). */
    renderHeaderFill(group, d, strongest) {
      if (strongest !== CHANGE_STATUS.ADDED) {
        return;
      }
      group.append("rect").attr("class", BOX_HEADER_FILL_CLASS).attr("width", d.rect.width).attr("height", HEADER_HEIGHT);
    }

    /** The header status glyph, the colour-independent signal for the box's status (spec 007 §3). */
    renderStatusGlyph(group, d, strongest, x) {
      const glyph = statusGlyph(strongest);
      if (!glyph) {
        return;
      }
      group.append("text").attr("class", STATUS_GLYPH_CLASS + " " + glyph.cssClass)
          .attr("x", x).attr("y", 16).text(glyph.symbol);
    }

    renderCompartmentRows(group, d) {
      let y = HEADER_HEIGHT;
      y = this.renderCompartment(group, d, d.compartments.constructors, y, false);
      y = this.renderCompartment(group, d, d.compartments.publicMethods, y, false);
      this.renderCompartment(group, d, d.compartments.revealedPrivateMethods, y, true);
    }

    renderCompartment(group, d, methods, startY, isPrivateCompartment) {
      if (methods.length === 0) {
        return startY;
      }
      let y = startY;
      if (startY > HEADER_HEIGHT) {
        group.append("line").attr("class", "compartment-rule" + (isPrivateCompartment ? " compartment-rule-private" : ""))
            .attr("x1", 0).attr("x2", d.rect.width).attr("y1", y).attr("y2", y);
        y += COMPARTMENT_RULE_HEIGHT;
      }
      for (const method of methods) {
        this.renderMemberRow(group, d, method, y, isPrivateCompartment);
        this.methodRowPositions.set(method.id, { classId: d.classId, rect: d.rect, rowY: d.rect.y + y + ROW_LABEL_BASELINE_OFFSET });
        y += ROW_HEIGHT;
      }
      return y;
    }

    renderMemberRow(group, d, method, y, isPrivateCompartment) {
      const underlined = this.underlinedMethodIds.has(method.id);
      const classes = rowCssClasses(method.status, underlined).concat(isPrivateCompartment ? ["member-row-private"] : []);
      const label = memberRowLabel(method, d.rect.width);
      group.append("text").attr("class", classes.join(" ")).attr(MEMBER_ROW_METHOD_ID_ATTRIBUTE, method.id)
          .attr("x", 12).attr("y", y + ROW_LABEL_BASELINE_OFFSET).text(label)
          .on("click", () => this.navigateToMethod(method.id));
      group.append("text").attr("class", "expander").attr("x", d.rect.width - 16).attr("y", y + ROW_LABEL_BASELINE_OFFSET).text("(+)")
          .on("click", () => this.toggleMethodRow(method.id, d.classId, this.expanderPathFor(d.classId)));
    }

    /** The expander path a method row's own expansion should be scoped beneath (spec 007 §6.2). */
    expanderPathFor(classId) {
      const box = this.controller.diagram.boxFor(classId);
      if (!box || box.revealingPaths.size === 0) {
        return this.openPillId;
      }
      // Shortest first, ties broken lexically. Set iteration order follows
      // insertion, so picking [0] handed out whichever path happened to be
      // added first: collapsing an unrelated sibling that owned that path then
      // retired an expansion made through it, leaving a row marked expanded
      // with none of its links. The shortest path is also the most durable —
      // it is the one closest to the entry point, so it survives the most
      // collapses.
      return [...box.revealingPaths]
          .sort((left, right) => left.length - right.length || left.localeCompare(right))[0];
    }

    /**
     * Recomputes every link a currently-expanded method row or class header
     * produces (spec 007 §4.3) — declarative rather than accumulated, so a
     * re-render never drifts from what is actually still expanded.
     */
    renderableLinks() {
      const links = [];
      const seenIds = new Set();
      this.addPillLink(links, seenIds);
      for (const methodId of this.expandedMethodRows.keys()) {
        const method = this.index.method(methodId);
        if (!method) {
          continue;
        }
        this.addMethodRowLinks(method, links, seenIds);
      }
      // A header expander reveals collaborator boxes, so it must draw the links
      // that justify them — otherwise the reader gets rectangles with nothing
      // joining them and no way to tell why they appeared.
      for (const classId of this.expandedClassHeaders) {
        this.addClassHeaderLinks(classId, links, seenIds);
      }
      return links;
    }

    /** The pill -> entry-method link (spec 007 §4.1), which no expander owns. */
    addPillLink(links, seenIds) {
      if (!this.openPillId || !this.entryUnderlinedMethodId) {
        return;
      }
      const linkId = this.openPillId + "->" + this.entryUnderlinedMethodId;
      if (seenIds.has(linkId)) {
        return;
      }
      seenIds.add(linkId);
      links.push({
        id: linkId,
        fromPill: true,
        sourcePillId: this.openPillId,
        targetMethodId: this.entryUnderlinedMethodId,
        style: LINK_STYLE.SOLID,
        crossModule: false,
        selfLink: false
      });
    }

    /** One link per call from any method of `classId` into another drawn class. */
    addClassHeaderLinks(classId, links, seenIds) {
      for (const method of this.index.methodsOfClass(classId)) {
        for (const edge of this.index.outgoing(method.id)) {
          if (!this.index.isDrawableCallTarget(edge)) {
            continue;
          }
          const target = this.index.method(edge.to);
          if (target.classId === classId || !isPublicApi(target)) {
            continue;
          }
          const linkId = method.id + "->" + target.id;
          if (seenIds.has(linkId)) {
            continue;
          }
          seenIds.add(linkId);
          links.push({
            id: linkId,
            sourceMethodId: method.id,
            targetMethodId: target.id,
            style: LINK_STYLE.SOLID,
            crossModule: edge.kind === EDGE_KIND.CROSS_MODULE,
            selfLink: false
          });
        }
      }
    }

    addMethodRowLinks(method, links, seenIds) {
      for (const edge of this.index.outgoing(method.id)) {
        if (!this.index.isDrawableCallTarget(edge)) {
          continue;
        }
        const target = this.index.method(edge.to);
        const style = target.classId !== method.classId || isPublicApi(target) ? LINK_STYLE.SOLID : LINK_STYLE.DASHED;
        const linkId = method.id + "->" + target.id;
        if (seenIds.has(linkId)) {
          continue;
        }
        seenIds.add(linkId);
        links.push({
          id: linkId,
          sourceMethodId: method.id,
          targetMethodId: target.id,
          style,
          crossModule: edge.kind === EDGE_KIND.CROSS_MODULE,
          selfLink: target.classId === method.classId
        });
      }
    }

    drawLinks() {
      const resolved = this.renderableLinks().map((link) => this.resolveEndpoints(link)).filter((link) => link !== null);
      const routable = mergeLinksIntoCollapsedBoxes(resolved);
      const lanes = assignLanesByGap(routable, (link) => link.gapKey);
      const links = this.routeAllLinks(routable, lanes);
      const selection = this.viewport.selectAll("path.class-link").data(links, (d) => d.id);
      selection.exit().remove();
      const entered = selection.enter().append("path");
      const merged = entered.merge(selection);
      merged
          .attr("class", (d) => linkCssClasses(d).join(" "))
          .attr("d", (d) => polylinePath(d.points))
          .attr("marker-end", "url(#arrowhead)")
          .on("mouseenter", (event, d) => this.setLinkHovered(d, true))
          .on("mouseleave", (event, d) => this.setLinkHovered(d, false));
    }

    /**
     * Hovering a link highlights it and both endpoints, so a long route can
     * be followed by eye (spec 007 §6.4.6): the path itself, and the
     * source/target member rows it connects.
     */
    setLinkHovered(link, hovered) {
      this.viewport.selectAll("path.class-link").filter((d) => d.id === link.id).classed(HOVERED_CSS_CLASS, hovered);
      this.setRowHovered(link.sourceMethodId, hovered);
      this.setRowHovered(link.targetMethodId, hovered);
    }

    /** Highlights (or un-highlights) one member row, identified by the method it renders, across every box. */
    setRowHovered(methodId, hovered) {
      // `nodes` holds raw DOM elements — `getAttribute`, not D3's `.attr()`.
      this.viewport.selectAll("text.member-row")
          .filter((d, i, nodes) => nodes[i].getAttribute(MEMBER_ROW_METHOD_ID_ATTRIBUTE) === methodId)
          .classed(HOVERED_CSS_CLASS, hovered);
    }

    /**
     * Resolves a method-level link into its two row positions plus the
     * inter-column gap key it crosses (spec 007 §6.4.1), or `null` if either
     * row is not currently drawn. The gap key is the pair of box **column
     * x's**, not the class pair: every link sharing that same visual corridor
     * must draw from one shared lane sequence, or two links between different
     * class pairs that happen to cross the very same gap could still be
     * allocated the same lane index and collide (AC11) — `source`/`target`
     * (class ids) are kept too, for {@link allocateLanes}'s own deterministic
     * tie-break ordering within a gap.
     */
    resolveEndpoints(link) {
      const targetPosition = this.rowOrHeaderPosition(link.targetMethodId);
      if (!targetPosition) {
        return null;
      }
      const sourcePosition = link.fromPill
          ? this.pillRowPosition()
          : this.rowOrHeaderPosition(link.sourceMethodId);
      if (!sourcePosition) {
        return null;
      }
      // Keyed by the corridor the link actually occupies — the source's right
      // edge — not by the (source x, target x) pair. Two links leaving the same
      // column share one physical corridor even when their targets differ, and
      // keying by the pair gave them independent lane sequences that both
      // started at 0, so they collided (AC11).
      const gapKey = String(sourcePosition.rect.x + sourcePosition.rect.width);
      return { ...link, source: sourcePosition.classId, target: targetPosition.classId, gapKey, sourcePosition, targetPosition };
    }

    /**
     * Where a link should attach for one method: its own row, or the class's
     * header when the box is collapsed.
     *
     * <p>A collapsed box has no rows, so without this every link touching it
     * would resolve to nothing and disappear — collapsing a class would quietly
     * delete the relationships that made it worth showing.
     */
    rowOrHeaderPosition(methodId) {
      const row = this.methodRowPositions.get(methodId);
      if (row) {
        return row;
      }
      const method = this.index.method(methodId);
      if (!method || !this.collapsedClassIds.has(method.classId) || !this.lastLayout) {
        return null;
      }
      const position = this.lastLayout.boxPositions.get(method.classId);
      if (!position) {
        return null;
      }
      return {
        classId: method.classId,
        rect: position.rect,
        rowY: position.rect.y + HEADER_HEIGHT / 2,
        atHeader: true
      };
    }

    /** The pill as a link source: its rect, with the row y at the pill's middle. */
    pillRowPosition() {
      if (!this.lastLayout) {
        return null;
      }
      const rect = this.lastLayout.pillRect;
      return { classId: this.openPillId, rect, rowY: rect.y + rect.height / 2 };
    }

    /**
     * Routes every link, one after another, carrying forward the segments each
     * one claims.
     *
     * <p>Order matters, so it is made deterministic — sorted by id — rather than
     * left to whatever order expansion happened to produce. Routing each link in
     * isolation is what let two of them land on identical segments: neither
     * could see the other.
     */
    routeAllLinks(routable, lanes) {
      const reserved = new Set();
      const ordered = [...routable].sort((left, right) => left.id.localeCompare(right.id));
      const routed = new Map();
      for (const link of ordered) {
        const result = this.routedLink(link, lanes.get(link.id), reserved);
        reserveTraversedSegments(result.points, reserved);
        routed.set(link.id, result);
      }
      // Restore the caller's order so the DOM join stays stable.
      return routable.map((link) => routed.get(link.id));
    }

    /** Routes one resolved link at its allocated lane (spec 007 §6.4), avoiding every other currently-drawn box. */
    routedLink(link, lane, reserved) {
      const obstacles = this.obstaclesBetween(link.sourcePosition, link.targetPosition);
      const points = routeOrthogonalLink({
        from: link.sourcePosition,
        to: link.targetPosition,
        lane,
        selfLink: link.selfLink
      }, obstacles, reserved);
      return { ...link, points };
    }

    /** Every currently-drawn box rect other than the link's own endpoints, as routing obstacles. */
    obstaclesBetween(sourcePosition, targetPosition) {
      // Compared by classId, not by rect identity. A collapsed box's endpoint
      // carries a freshly built rect, so an identity check failed to recognise
      // the link's own target and the router was made to route around the very
      // box it was heading for — which is how lines ended up cutting through
      // other boxes to get there.
      const endpointClassIds = new Set([sourcePosition.classId, targetPosition.classId]);
      const obstacles = [];
      for (const [classId, position] of this.lastLayout ? this.lastLayout.boxPositions : []) {
        if (!endpointClassIds.has(classId)) {
          obstacles.push({ rect: position.rect });
        }
      }
      return obstacles;
    }

    renderSidePanel() {
      const panel = document.getElementById("side-panel");
      panel.innerHTML = "";
      if (!this.selection) {
        return;
      }
      if (this.selection.kind === SELECTION_KIND.CLASS) {
        panel.appendChild(buildClassPanelElement(this.index, this.selection.classId, (id) => this.navigateToClass(id)));
      } else if (this.selection.kind === SELECTION_KIND.METHOD) {
        panel.appendChild(buildMethodPanelElement(this.index, this.selection.methodId, (id) => this.navigateToClass(id)));
      }
    }
  }

  // ---------------------------------------------------------------------
  // Side panel DOM (spec 007 §4.2): class-name click shows class-granular
  // calls/called-by plus the full class source; method-row click shows
  // called-by plus the method source.
  // ---------------------------------------------------------------------

  function buildClassPanelElement(index, classId, onNavigateToClass) {
    const fragment = document.createDocumentFragment();
    const panelData = buildClassPanelData(index, classId);
    if (!panelData) {
      return fragment;
    }
    fragment.appendChild(section("Class", textElement("code", panelData.fqn)));
    fragment.appendChild(badgeSection(panelData.layer, panelData.moduleName));
    fragment.appendChild(section("File", textElement("div", panelData.file)));
    if (panelData.javadoc) {
      fragment.appendChild(section("Javadoc", textElement("div", panelData.javadoc)));
    }
    fragment.appendChild(classIdListSection("Calls", panelData.callsClassIds, index, onNavigateToClass));
    fragment.appendChild(classIdListSection("Called by", panelData.calledByClassIds, index, onNavigateToClass));
    fragment.appendChild(section("Source", sourceElement(panelData.source)));
    return fragment;
  }

  function buildMethodPanelElement(index, methodId, onNavigateToClass) {
    const fragment = document.createDocumentFragment();
    const panelData = buildMethodPanelData(index, methodId);
    if (!panelData) {
      return fragment;
    }
    fragment.appendChild(section("Signature", textElement("code", panelData.signature)));
    fragment.appendChild(badgeSection(panelData.layer, panelData.moduleName));
    fragment.appendChild(section("Location", textElement("div",
        panelData.file + ":" + panelData.lineStart + "-" + panelData.lineEnd)));
    if (panelData.javadoc) {
      fragment.appendChild(section("Javadoc", textElement("div", panelData.javadoc)));
    }
    fragment.appendChild(classIdListSection("Called by", panelData.calledByClassIds, index, onNavigateToClass));
    fragment.appendChild(section("Source", sourceElement(panelData.source)));
    return fragment;
  }

  function classIdListSection(title, classIds, index, onNavigate) {
    const list = document.createElement("ul");
    list.className = "nav-list";
    for (const classId of classIds) {
      const owningClass = index.classOf(classId);
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.textContent = owningClass ? owningClass.simpleName : classId;
      link.dataset.classId = classId;
      link.addEventListener("click", () => onNavigate(classId));
      item.appendChild(link);
      list.appendChild(item);
    }
    return section(title, list);
  }

  function section(title, content) {
    const wrapper = document.createElement("div");
    wrapper.className = "panel-section";
    const heading = document.createElement("h3");
    heading.textContent = title;
    wrapper.appendChild(heading);
    wrapper.appendChild(content);
    return wrapper;
  }

  function badgeSection(layer, moduleName) {
    const wrapper = document.createElement("div");
    wrapper.className = "panel-section";
    if (layer) {
      wrapper.appendChild(badge("badge-layer", layer));
    }
    if (moduleName) {
      wrapper.appendChild(badge("badge-module", moduleName));
    }
    return wrapper;
  }

  function badge(className, text) {
    const span = document.createElement("span");
    span.className = "badge " + className;
    span.textContent = text;
    return span;
  }

  function textElement(tag, text) {
    const element = document.createElement(tag);
    element.textContent = text;
    return element;
  }

  function sourceElement(sourceText) {
    const element = document.createElement("pre");
    element.className = SOURCE_CLASS;
    for (const token of tokenizeJava(sourceText)) {
      if (token.kind === null) {
        element.appendChild(document.createTextNode(token.text));
        continue;
      }
      const span = document.createElement("span");
      span.className = TOKEN_CLASS_PREFIX + token.kind;
      // textContent, never innerHTML: this is untrusted source text, and the
      // whole escaping model of the report depends on never parsing it as markup.
      span.textContent = token.text;
      element.appendChild(span);
    }
    return element;
  }

  /**
   * Splits Java source into display tokens for syntax highlighting.
   *
   * <p>Hand-written rather than a highlighting library: the report inlines
   * everything it needs and must keep working over `file://` with no network, so
   * a dependency would have to be vendored whole for one panel. This recognises
   * what actually helps a reader skim a method — comments, strings, keywords,
   * numbers, annotations — and deliberately leaves everything else alone rather
   * than pretending to be a parser.
   */
  function tokenizeJava(source) {
    const tokens = [];
    let plain = "";
    let index = 0;
    const pushPlain = () => {
      if (plain !== "") {
        tokens.push({ kind: null, text: plain });
        plain = "";
      }
    };
    const pushToken = (kind, text) => {
      pushPlain();
      tokens.push({ kind, text });
      index += text.length;
    };

    while (index < source.length) {
      const rest = source.slice(index);
      const match = JAVA_TOKEN_PATTERNS.find((pattern) => pattern.regex.test(rest));
      if (match) {
        pushToken(match.kind, rest.match(match.regex)[0]);
        continue;
      }
      const word = rest.match(JAVA_WORD);
      if (word) {
        if (JAVA_KEYWORDS.has(word[0])) {
          pushToken("keyword", word[0]);
        } else {
          plain += word[0];
          index += word[0].length;
        }
        continue;
      }
      plain += source[index];
      index += 1;
    }
    pushPlain();
    return tokens;
  }

  // ---------------------------------------------------------------------
  // Module overview (spec §7, kept from feature/6): "which module depends
  // on which", from the real aggregated CROSS_MODULE edges.
  // ---------------------------------------------------------------------

  /**
   * Projects the raw `moduleDependencies` pairs into overview rows with real
   * module names — "which module depends on which", resolved from ids to
   * something a reader recognises without cross-referencing the module list.
   *
   * @return one row per aggregated CROSS_MODULE dependency
   */
  function buildModuleOverview(index) {
    return index.data.moduleDependencies.map((dependency) => {
      const fromModule = index.module(dependency.fromModuleId);
      const toModule = index.module(dependency.toModuleId);
      return {
        fromModuleId: dependency.fromModuleId,
        toModuleId: dependency.toModuleId,
        fromName: fromModule ? fromModule.name : dependency.fromModuleId,
        toName: toModule ? toModule.name : dependency.toModuleId
      };
    });
  }

  // ---------------------------------------------------------------------
  // Theme toggle (spec §7, kept from feature/6).
  // ---------------------------------------------------------------------

  const THEME_STORAGE_KEY = "codemap-theme";
  const THEME_ATTRIBUTE = "data-theme";
  const THEME_LIGHT = "light";
  const THEME_DARK = "dark";

  /**
   * Decides the theme to open with: an explicit stored choice always wins;
   * otherwise the OS preference is honoured; otherwise dark, the report's
   * original look.
   *
   * @param env {getStoredTheme, prefersLight} — injected so this can be
   *        tested as pure decision logic, without a `window` or `document`
   */
  function initialTheme(env) {
    const stored = env.getStoredTheme();
    if (stored === THEME_LIGHT || stored === THEME_DARK) {
      return stored;
    }
    return env.prefersLight() ? THEME_LIGHT : THEME_DARK;
  }

  function readStoredTheme() {
    try {
      return window.localStorage ? window.localStorage.getItem(THEME_STORAGE_KEY) : null;
    } catch (error) {
      return null;
    }
  }

  function prefersLightColorScheme() {
    return typeof window.matchMedia === "function" && window.matchMedia("(prefers-color-scheme: light)").matches;
  }

  function persistTheme(theme) {
    try {
      if (window.localStorage) {
        window.localStorage.setItem(THEME_STORAGE_KEY, theme);
      }
    } catch (error) {
      // Persisting the theme is a convenience; a read-only or absent storage
      // must not break the toggle itself (degrade, don't fail).
    }
  }

  /**
   * Applies a theme by setting it as an attribute on the document root, so
   * every colour switches through the CSS custom properties in report.css.
   *
   * @param root the element the theme attribute is set on, injected so this
   *        stays testable without a real `document`
   */
  function applyTheme(theme, button, root) {
    root.setAttribute(THEME_ATTRIBUTE, theme);
    button.textContent = theme === THEME_DARK ? "Light theme" : "Dark theme";
  }

  /**
   * The entry-point list folds away, giving the canvas the whole width.
   *
   * <p>Once a reader has picked an endpoint the list is dead weight, and a wide
   * diagram is exactly what the map is for.
   */
  function wireEntryPointToggle() {
    const button = document.getElementById("toggle-entry-points");
    const app = document.getElementById("app");
    if (!button || !app) {
      return;
    }
    button.addEventListener("click", () => {
      const hidden = app.classList.toggle(ENTRY_POINTS_HIDDEN_CLASS);
      button.textContent = hidden ? "Show list" : "Hide list";
      button.title = hidden ? "Show the entry-point list" : "Hide the entry-point list";
      button.classList.toggle("active", hidden);
    });
  }

  /**
   * Drags the boundary between canvas and side panel.
   *
   * <p>The panel holds whole class bodies, so how much room it deserves depends
   * on what the reader is doing — reading source wants it wide, following links
   * wants it out of the way.
   */
  function wireSidePanelResizer() {
    const resizer = document.getElementById("side-panel-resizer");
    const app = document.getElementById("app");
    if (!resizer || !app) {
      return;
    }
    let dragging = false;

    const widthFromPointer = (clientX) => {
      const proposed = app.getBoundingClientRect().right - clientX;
      return Math.min(SIDE_PANEL_MAX_WIDTH, Math.max(SIDE_PANEL_MIN_WIDTH, proposed));
    };

    resizer.addEventListener("mousedown", (event) => {
      dragging = true;
      resizer.classList.add("dragging");
      document.body.classList.add("resizing");
      event.preventDefault();
    });
    document.addEventListener("mousemove", (event) => {
      if (dragging) {
        app.style.setProperty("--side-panel-width", widthFromPointer(event.clientX) + "px");
      }
    });
    document.addEventListener("mouseup", () => {
      dragging = false;
      resizer.classList.remove("dragging");
      document.body.classList.remove("resizing");
    });
  }

  function wireThemeToggle() {
    const header = document.querySelector("header .controls");
    const button = document.createElement("button");
    button.id = "theme-toggle-button";
    button.type = "button";
    header.appendChild(button);

    const root = document.documentElement;
    const env = { getStoredTheme: readStoredTheme, prefersLight: prefersLightColorScheme };
    applyTheme(initialTheme(env), button, root);
    button.addEventListener("click", () => {
      const next = root.getAttribute(THEME_ATTRIBUTE) === THEME_DARK ? THEME_LIGHT : THEME_DARK;
      applyTheme(next, button, root);
      persistTheme(next);
    });
  }

  window.CodemapInternal = {
    EDGE_KIND,
    CHANGE_STATUS,
    VISIBILITY,
    ROOT_PAGE_LABEL,
    SOURCE_CLASS,
    TRANSITION_MS,
    ROOT_MARGIN_X,
    ROOT_MARGIN_Y,
    LANE_SPACING,
    CodemapIndex,
    groupBy,
    strongestStatus,
    visibilityMarker,
    stereotypeLabel,
    statusGlyph,
    boxStrongestStatus,
    buildCompartments,
    boxCssClasses,
    rowCssClasses,
    linkCssClasses,
    ClassBox,
    DiagramState,
    DiagramController,
    DiagramView,
    assignColumns,
    orderColumnByBarycentre,
    routeOrthogonalLink,
    polylinePath,
    allocateLanes,
    applyBoxOffsets,
    tokenizeJava,
    isDragHandle,
    mergeLinksIntoCollapsedBoxes,
    reserveTraversedSegments,
    searchCorridorPath,
    assignLanesByGap,
    buildClassPanelData,
    buildMethodPanelData,
    filterEntryPoints,
    boxHeight,
    boxWidthFor,
    memberRowLabel,
    layoutDiagram,
    BOX_WIDTH,
    BOX_MAX_WIDTH,
    PILL_WIDTH,
    PILL_HEIGHT,
    ROW_HEIGHT,
    HEADER_HEIGHT,
    buildModuleOverview,
    THEME_LIGHT,
    THEME_DARK,
    THEME_ATTRIBUTE,
    THEME_STORAGE_KEY,
    initialTheme,
    applyTheme
  };

  // ---------------------------------------------------------------------
  // Bootstrap: wires the entry-point picker, filters, module overview, and
  // theme toggle around one {@link DiagramView}.
  // ---------------------------------------------------------------------

  function bootstrap(data) {
    const index = new CodemapIndex(data);
    const view = new DiagramView(index);
    window.CodemapReport = { index, view };
    wireEntryPointPicker(index, view);
    wireModuleOverview(index);
    wireThemeToggle();
    wireEntryPointToggle();
    wireSidePanelResizer();
  }

  const MODULE_OVERVIEW_BUTTON_ID = "module-overview-button";
  const MODULE_OVERVIEW_PANEL_ID = "module-overview-panel";
  const MODULE_OVERVIEW_VISIBLE_CLASS = "visible";

  function wireModuleOverview(index) {
    const header = document.querySelector("header .controls");
    const button = document.createElement("button");
    button.id = MODULE_OVERVIEW_BUTTON_ID;
    button.type = "button";
    button.textContent = "Module overview";
    header.appendChild(button);

    const panel = document.createElement("div");
    panel.id = MODULE_OVERVIEW_PANEL_ID;
    panel.className = "module-overview-panel";
    document.getElementById("app").appendChild(panel);
    renderModuleOverviewPanel(panel, index);

    button.addEventListener("click", () => {
      const isVisible = panel.classList.toggle(MODULE_OVERVIEW_VISIBLE_CLASS);
      button.classList.toggle("active", isVisible);
    });
  }

  function renderModuleOverviewPanel(panel, index) {
    panel.innerHTML = "";
    const overview = buildModuleOverview(index);
    if (overview.length === 0) {
      const empty = document.createElement("div");
      empty.className = "placeholder";
      empty.textContent = "No cross-module dependencies.";
      panel.appendChild(empty);
      return;
    }
    const list = document.createElement("ul");
    list.className = "module-overview-list";
    for (const row of overview) {
      const item = document.createElement("li");
      item.textContent = row.fromName + " → " + row.toName;
      list.appendChild(item);
    }
    panel.appendChild(list);
  }

  /**
   * Wires the entry-point picker (spec 007 §4.1): a filterable list of pills
   * a reader clicks to open a class box, plus search/layer/module filtering
   * (spec §7, kept from feature/6).
   */
  function wireEntryPointPicker(index, view) {
    const searchInput = document.getElementById("search-input");
    const layerFilter = document.getElementById("layer-filter");
    const moduleFilter = document.getElementById("module-filter");

    populateLayerOptions(layerFilter, index);
    populateModuleOptions(moduleFilter, index);

    const criteria = { searchTerm: "", layer: "", moduleId: "" };
    const rerenderPicker = () => renderEntryPointPicker(index, view, criteria);

    searchInput.addEventListener("input", (event) => {
      criteria.searchTerm = event.target.value;
      rerenderPicker();
    });
    layerFilter.addEventListener("change", (event) => {
      criteria.layer = event.target.value;
      rerenderPicker();
    });
    moduleFilter.addEventListener("change", (event) => {
      criteria.moduleId = event.target.value;
      rerenderPicker();
    });

    rerenderPicker();
  }

  function renderEntryPointPicker(index, view, criteria) {
    const list = document.getElementById("entry-point-list");
    if (!list) {
      return;
    }
    list.innerHTML = "";
    const filtered = filterEntryPoints(index.data.entryPoints, index, criteria);
    for (const entryPoint of filtered) {
      list.appendChild(entryPointPickerItem(entryPoint, view));
    }
  }

  function entryPointPickerItem(entryPoint, view) {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.type = "button";
    // Marked by what the whole call chain reaches, not by the handler's own
    // status: the point of the list is to say which endpoints this diff touches,
    // and a PR routinely leaves a handler alone while rewriting what it calls.
    const status = view.index.reachableStatusOf(entryPoint);
    const glyph = statusGlyph(status);
    if (glyph) {
      const marker = document.createElement("span");
      marker.className = ENTRY_POINT_MARKER_CLASS + " " + glyph.cssClass;
      marker.textContent = glyph.symbol;
      marker.title = ENTRY_POINT_STATUS_TITLE[status] || "";
      button.appendChild(marker);
      button.classList.add(STATUS_CSS_CLASS[status]);
    }
    const label = document.createElement("span");
    label.textContent = entryPoint.label === "" ? ROOT_PAGE_LABEL : entryPoint.label;
    button.appendChild(label);
    button.addEventListener("click", () => view.openEntryPointPill(entryPoint.id));
    item.appendChild(button);
    return item;
  }

  function populateLayerOptions(select, index) {
    const layers = new Set(index.data.classes.map((c) => c.layer));
    for (const layer of layers) {
      const option = document.createElement("option");
      option.value = layer;
      option.textContent = layer;
      select.appendChild(option);
    }
  }

  function populateModuleOptions(select, index) {
    for (const module of index.data.modules) {
      const option = document.createElement("option");
      option.value = module.id;
      option.textContent = module.name;
      select.appendChild(option);
    }
  }

  if (DATA) {
    bootstrap(DATA);
  }
})();
