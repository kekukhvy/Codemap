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

  const STATUS_GLYPH_CLASS = "status-glyph";
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
        box.revealingPaths.delete(revealingPath);
        this.forgetPrivateRowsFor(box, revealingPath);
        if (box.revealingPaths.size === 0) {
          this.boxes.delete(classId);
        }
      }
    }

    forgetPrivateRowsFor(box, revealingPath) {
      for (const paths of box.revealedPrivateRowPaths.values()) {
        paths.delete(revealingPath);
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
    return parentPath + ">" + methodId;
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
      const method = this.index.method(entryPoint.methodId);
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
      this.diagram.ensureBox(targetMethod.classId, path);
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
      const diff = barycentreOf(a) - barycentreOf(b);
      return diff !== 0 ? diff : a.localeCompare(b);
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
  function routeOrthogonalLink(link, obstacles) {
    if (link.selfLink) {
      return routeSelfLink(link, obstacles);
    }
    const sourceExitX = link.from.rect.x + link.from.rect.width;
    const targetEntryX = link.to.rect.x;
    const sourceY = link.from.rowY;
    const targetY = link.to.rowY;
    const laneX = laneCorridorX(sourceExitX, targetEntryX, link.lane, sourceY, targetY, obstacles);
    const sourceStubX = sourceExitX + LANE_SPACING * (link.lane + 1);
    const targetStubX = targetEntryX - LANE_SPACING * (link.lane + 1);
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
  function laneCorridorX(sourceExitX, targetEntryX, lane, sourceY, targetY, obstacles) {
    const baseX = Math.min(sourceExitX, targetEntryX);
    const laneOffset = LANE_SPACING * (lane + 1);
    const clearanceFloor = obstacleClearanceFloor(baseX, sourceY, targetY, obstacles);
    return Math.max(baseX + laneOffset, clearanceFloor + laneOffset);
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
  const COLUMN_GAP = 90;
  const ROW_HEIGHT = 20;
  const HEADER_HEIGHT = 26;
  const COMPARTMENT_RULE_HEIGHT = 6;
  const BOX_VERTICAL_GAP = 24;
  const PILL_WIDTH = 160;
  const PILL_HEIGHT = 34;
  /** Baseline drop from a row's top edge to its text, so glyph and label align. */
  const ROW_LABEL_BASELINE_OFFSET = 14;

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
  function layoutDiagram(diagram, index, rootPillId) {
    const classIds = [...diagram.boxes.keys()];
    const links = [...pillLinksFor(index, rootPillId), ...diagramLinksFor(diagram, index)];
    const columns = assignColumns([rootPillId, ...classIds], links, rootPillId);

    const byColumn = groupBy(classIds, (classId) => columns.get(classId));
    const boxPositions = new Map();
    const parentPositions = { [rootPillId]: 0 };
    const maxColumn = Math.max(0, ...classIds.map((id) => columns.get(id)));
    for (let column = 1; column <= maxColumn; column++) {
      const boxesInColumn = byColumn.get(column) || [];
      const ordered = orderColumnByBarycentre(boxesInColumn, links, parentPositions);
      placeColumn(ordered, column, diagram, index, boxPositions, parentPositions);
    }
    const pillRect = { x: ROOT_MARGIN_X, y: ROOT_MARGIN_Y, width: PILL_WIDTH, height: PILL_HEIGHT };
    return { boxPositions, pillRect };
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

  function placeColumn(orderedClassIds, column, diagram, index, boxPositions, parentPositions) {
    let cursorY = ROOT_MARGIN_Y;
    const columnX = ROOT_MARGIN_X + PILL_WIDTH + COLUMN_GAP + (column - 1) * (BOX_WIDTH + COLUMN_GAP);
    for (const classId of orderedClassIds) {
      const box = diagram.boxFor(classId);
      const compartments = buildCompartments(index, classId, box.revealedPrivateMethodIds);
      const height = boxHeight(compartments);
      const rect = { x: columnX, y: cursorY, width: BOX_WIDTH, height };
      boxPositions.set(classId, { rect, column, compartments });
      parentPositions[classId] = cursorY;
      cursorY += height + BOX_VERTICAL_GAP;
    }
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
      this.expandedMethodRows = new Set();
      this.openPillId = null;
      this.hasFittedView = false;
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

    /** Opens an entry-point pill (spec 007 §4.1): draws its class, underlines the handler row. */
    openEntryPointPill(entryPointId) {
      const result = this.controller.openEntryPoint(entryPointId);
      this.openPillId = entryPointId;
      this.underlinedMethodIds.add(result.underlinedMethodId);
      this.render();
      return result;
    }

    /** The class-header `(+)`/`(−)` expander (spec 007 §4.3). */
    toggleClassHeader(classId) {
      const box = this.controller.diagram.boxFor(classId);
      if (box && box.expandedHeader) {
        this.controller.collapseClassHeader(classId);
        box.expandedHeader = false;
      } else {
        this.controller.expandClassHeader(classId);
        const reopened = this.controller.diagram.boxFor(classId);
        if (reopened) {
          reopened.expandedHeader = true;
        }
      }
      this.render();
    }

    /** The method-row `(+)`/`(−)` expander (spec 007 §4.3). */
    toggleMethodRow(methodId, ownerClassId, parentPath) {
      if (this.expandedMethodRows.has(methodId)) {
        this.collapseMethodRow(methodId, parentPath);
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
      this.expandedMethodRows.add(methodId);
      return result;
    }

    collapseMethodRow(methodId, parentPath) {
      this.controller.collapseMethodRow(methodId, parentPath);
      this.expandedMethodRows.delete(methodId);
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
      this.lastLayout = layoutDiagram(this.controller.diagram, this.index, this.openPillId);
      this.drawPill(this.lastLayout);
      this.drawBoxes(this.lastLayout);
      this.drawLinks();
    }

    /** The entry-point pill itself (spec 007 §2.1, §4.1). */
    drawPill(layout) {
      const entryPoint = this.index.data.entryPoints.find((candidate) => candidate.id === this.openPillId);
      const selection = this.viewport.selectAll("g.entry-point-pill").data([entryPoint], (d) => d.id);
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
      group.append("rect").attr("class", "box-rect").attr("width", d.rect.width).attr("height", d.rect.height);
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
      this.renderStatusGlyph(group, d, strongest);
      group.append("text").attr("class", "expander").attr("x", d.rect.width - 16).attr("y", 16).text("(+)")
          .on("click", () => this.toggleClassHeader(d.classId));
    }

    /** The green header fill an `ADDED` box gets, in addition to its solid green border (spec 007 §3). */
    renderHeaderFill(group, d, strongest) {
      if (strongest !== CHANGE_STATUS.ADDED) {
        return;
      }
      group.append("rect").attr("class", BOX_HEADER_FILL_CLASS).attr("width", d.rect.width).attr("height", HEADER_HEIGHT);
    }

    /** The header status glyph, the colour-independent signal for the box's status (spec 007 §3). */
    renderStatusGlyph(group, d, strongest) {
      const glyph = statusGlyph(strongest);
      if (!glyph) {
        return;
      }
      group.append("text").attr("class", STATUS_GLYPH_CLASS + " " + glyph.cssClass)
          .attr("x", d.rect.width - 32).attr("y", 16).text(glyph.symbol);
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
      const label = visibilityMarker(method.visibility) + " " + method.signature;
      group.append("text").attr("class", classes.join(" ")).attr(MEMBER_ROW_METHOD_ID_ATTRIBUTE, method.id)
          .attr("x", 12).attr("y", y + ROW_LABEL_BASELINE_OFFSET).text(label)
          .on("click", () => this.navigateToMethod(method.id));
      group.append("text").attr("class", "expander").attr("x", d.rect.width - 16).attr("y", y + ROW_LABEL_BASELINE_OFFSET).text("(+)")
          .on("click", () => this.toggleMethodRow(method.id, d.classId, this.expanderPathFor(d.classId)));
    }

    /** The expander path a method row's own expansion should be scoped beneath (spec 007 §6.2). */
    expanderPathFor(classId) {
      const box = this.controller.diagram.boxFor(classId);
      return box ? [...box.revealingPaths][0] : this.openPillId;
    }

    /**
     * Recomputes every link a currently-expanded method row or class header
     * produces (spec 007 §4.3) — declarative rather than accumulated, so a
     * re-render never drifts from what is actually still expanded.
     */
    renderableLinks() {
      const links = [];
      const seenIds = new Set();
      for (const methodId of this.expandedMethodRows) {
        const method = this.index.method(methodId);
        if (!method) {
          continue;
        }
        this.addMethodRowLinks(method, links, seenIds);
      }
      return links;
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
      const routable = this.renderableLinks().map((link) => this.resolveEndpoints(link)).filter((link) => link !== null);
      const lanes = assignLanesByGap(routable, (link) => link.gapKey);
      const links = routable.map((link) => this.routedLink(link, lanes.get(link.id)));
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
      this.viewport.selectAll("text.member-row")
          .filter((d, i, nodes) => nodes[i].attr(MEMBER_ROW_METHOD_ID_ATTRIBUTE) === methodId)
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
      const sourcePosition = this.methodRowPositions.get(link.sourceMethodId);
      const targetPosition = this.methodRowPositions.get(link.targetMethodId);
      if (!sourcePosition || !targetPosition) {
        return null;
      }
      const gapKey = sourcePosition.rect.x + ">" + targetPosition.rect.x;
      return { ...link, source: sourcePosition.classId, target: targetPosition.classId, gapKey, sourcePosition, targetPosition };
    }

    /** Routes one resolved link at its allocated lane (spec 007 §6.4), avoiding every other currently-drawn box. */
    routedLink(link, lane) {
      const obstacles = this.obstaclesBetween(link.sourcePosition, link.targetPosition);
      const points = routeOrthogonalLink({
        from: link.sourcePosition,
        to: link.targetPosition,
        lane,
        selfLink: link.selfLink
      }, obstacles);
      return { ...link, points };
    }

    /** Every currently-drawn box rect other than the link's own endpoints, as routing obstacles. */
    obstaclesBetween(sourcePosition, targetPosition) {
      const obstacles = [];
      for (const [classId, position] of this.lastLayout ? this.lastLayout.boxPositions : []) {
        if (position.rect !== sourcePosition.rect && position.rect !== targetPosition.rect) {
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
    const element = textElement("pre", sourceText);
    element.className = SOURCE_CLASS;
    return element;
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
    assignLanesByGap,
    buildClassPanelData,
    buildMethodPanelData,
    filterEntryPoints,
    boxHeight,
    layoutDiagram,
    BOX_WIDTH,
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
    button.textContent = entryPoint.label === "" ? ROOT_PAGE_LABEL : entryPoint.label;
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
