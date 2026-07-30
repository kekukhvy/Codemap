(function () {
  "use strict";

  const DATA = window.__CODEMAP_DATA__;

  const NODE_KIND = {
    MODULE: "MODULE",
    ENTRY_POINT: "ENTRY_POINT",
    CLASS: "CLASS",
    METHOD: "METHOD",
    TYPE_REF: "TYPE_REF"
  };

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

  const ALREADY_ABOVE_LABEL = "↗ already above";
  const ROOT_PAGE_LABEL = "/ (root)";

  const NODE_RADIUS = 6;

  // The tree grows left-to-right (depth advances x, siblings stack in y)
  // rather than top-down. Java identifiers are long, and a label is always
  // drawn to the right of its node (spec-neutral choice, §3.1 only requires
  // the tree to grow on expansion) — stacking siblings vertically means two
  // labels can only ever collide if they are closer than a text line's
  // height, never because of label width, which is unbounded for a top-down
  // layout with wide siblings.
  const LEVEL_WIDTH = 220;
  const MIN_SIBLING_SPACING = 34;
  const ROOT_MARGIN_X = 40;
  const ROOT_MARGIN_Y = 40;
  const TRANSITION_MS = 250;
  const SOURCE_CLASS = "source";

  // How far right of a node its outgoing edges start, so they clear the node's
  // own label instead of crossing through it.
  const LABEL_CLEARANCE = 90;

  /**
   * Indexes the raw view model for O(1) lookups the tree builder and side
   * panel both need repeatedly: methods and classes by id, edges by source,
   * and edges by target (the "Called by" reverse lookup).
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

    /** Whether any node has a non-unchanged, non-null status. */
    hasChanges() {
      const changed = (s) => s && s !== CHANGE_STATUS.UNCHANGED;
      return this.data.methods.some((m) => changed(m.status))
          || this.data.classes.some((c) => changed(c.status))
          || this.data.removedMethods.length > 0;
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

  /** A unique key for one occurrence of a node in the rendered tree. */
  let nextNodeSequence = 0;

  function makeTreeNode(kind, id, label, parent, extra) {
    nextNodeSequence += 1;
    return Object.assign(
        {
          nodeKey: "n" + nextNodeSequence,
          kind,
          id,
          label,
          parent: parent || null,
          children: [],
          expanded: false,
          collapsedRevisit: false,
          revisitTargetKey: null,
          collapsedCrossModule: false,
          targetModuleId: null,
          focused: false,
          status: null,
          depth: parent ? parent.depth + 1 : 0
        },
        extra || {});
  }

  /**
   * Builds one level of the lazy tree (spec §3.1). The tree grows only
   * through methods — a CLASS node is context for the method beneath it, not
   * a step in the chain, so it is inserted automatically alongside a method
   * rather than being expanded into separately:
   *
   * <ul>
   *   <li>An entry point reveals its owning class and, beneath it, only the
   *       one method that handles it — not every method the class declares.
   *   <li>Expanding a method reveals, per outgoing call, the callee's owning
   *       class and the called method beneath it — except when the callee's
   *       class is the one already shown one hop up, where a second class
   *       header would be redundant and the callee is parented directly.
   * </ul>
   *
   * <p>A method already open higher in the current branch renders collapsed
   * with a revisit badge instead of being expanded again (the revisit check
   * keys on the method only, so an intervening CLASS node is transparent to
   * it — see {@link findAncestorMethod}), which is what keeps cycles from
   * hanging the UI.
   */
  class TreeBuilder {
    constructor(index) {
      this.index = index;
    }

    buildModuleRoots() {
      return this.index.data.modules.map((module) =>
          makeTreeNode(NODE_KIND.MODULE, module.id, module.name, null, {}));
    }

    /**
     * Expands one node in place, attaching its next level of children.
     *
     * <p>A collapsed revisit never materialises further children (spec §3.1):
     * it already renders as a link back to the original occurrence, and
     * expanding it would re-walk the same cycle the revisit rule exists to
     * terminate.
     *
     * <p>A collapsed cross-module callee never materialises children either
     * (spec §3.2.2): a module boundary is collapsed by default so a foreign
     * module's private call chain does not get inlined into this one's tree.
     * {@link GraphView#toggle} handles it separately, by jumping to the
     * target module instead of calling this method.
     *
     * <p>A CLASS node never expands into the diagram at all: it is drawn once,
     * already carrying its one relevant method, when its parent expanded.
     * Clicking a CLASS node only opens the side panel (see
     * {@link GraphView#select}); the canvas never grows from it.
     */
    expand(node) {
      if (node.kind === NODE_KIND.CLASS) {
        return;
      }
      if (node.expanded || node.collapsedRevisit || node.collapsedCrossModule) {
        return;
      }
      node.expanded = true;
      node.children = this.childrenOf(node);
    }

    childrenOf(node) {
      switch (node.kind) {
        case NODE_KIND.MODULE:
          return this.entryPointChildren(node);
        case NODE_KIND.ENTRY_POINT:
          return this.handlerChildren(node);
        case NODE_KIND.METHOD:
          return this.callChildren(node);
        default:
          return [];
      }
    }

    entryPointChildren(moduleNode) {
      return this.index.entryPointsOf(moduleNode.id).map((entryPoint) => {
        const label = entryPoint.label === "" ? ROOT_PAGE_LABEL : entryPoint.label;
        return makeTreeNode(NODE_KIND.ENTRY_POINT, entryPoint.id, label, moduleNode, {
          methodId: entryPoint.methodId,
          entryPointKind: entryPoint.kind,
          detectedBy: entryPoint.detectedBy
        });
      });
    }

    /** An entry point reveals its owning class, holding only the handler. */
    handlerChildren(entryPointNode) {
      const methodId = entryPointNode.methodId;
      const method = this.index.method(methodId);
      if (!method) {
        return [];
      }
      const classNode = this.classNode(entryPointNode, method.classId);
      const handlerNode = this.methodNode(classNode, methodId, null);
      handlerNode.focused = true;
      classNode.children = [handlerNode];
      return [classNode];
    }

    /** A CLASS node: context for the method(s) shown beneath it, never itself expandable. */
    classNode(parentNode, classId) {
      const owningClass = this.index.classOf(classId);
      const label = owningClass ? owningClass.simpleName : classId;
      return makeTreeNode(NODE_KIND.CLASS, classId, label, parentNode, { classId });
    }

    methodNode(parentNode, methodId, edgeKind) {
      const method = this.index.method(methodId);
      const node = makeTreeNode(NODE_KIND.METHOD, methodId, method.name, parentNode, {
        methodId,
        status: method.status,
        edgeKind: edgeKind || null
      });
      return node;
    }

    /**
     * Every outgoing call becomes one hop: a same-class callee is parented
     * directly under the caller (no redundant class node for the class
     * already shown one hop up); a cross-class callee gets its own CLASS
     * node first, carrying the edge's kind so the link from the caller
     * still renders dashed/solid/heavy per spec §3.3.
     */
    callChildren(methodNode) {
      if (methodNode.collapsedRevisit) {
        return [];
      }
      const callerClassId = this.callerClassId(methodNode);
      const edges = this.index.outgoing(methodNode.methodId)
          .filter((edge) => edge.kind !== EDGE_KIND.USES_TYPE)
          .filter((edge) => this.index.method(edge.to));
      return edges.map((edge) => this.calleeHop(methodNode, edge, callerClassId));
    }

    /** The class the calling method belongs to, so a same-class callee can be detected. */
    callerClassId(methodNode) {
      const method = this.index.method(methodNode.methodId);
      return method ? method.classId : null;
    }

    calleeHop(methodNode, edge, callerClassId) {
      const calleeMethod = this.index.method(edge.to);
      if (edge.kind === EDGE_KIND.CROSS_MODULE) {
        return this.crossModuleHop(methodNode, edge);
      }
      if (calleeMethod.classId === callerClassId) {
        return this.calleeMethodNode(methodNode, edge, methodNode);
      }
      const classNode = this.classNode(methodNode, calleeMethod.classId);
      classNode.edgeKind = edge.kind;
      const calleeNode = this.calleeMethodNode(classNode, edge, methodNode);
      classNode.children = [calleeNode];
      return classNode;
    }

    /** A callee in the same class as the caller: no class node, direct method-to-method hop. */
    calleeMethodNode(parentNode, edge, revisitScopeNode) {
      const node = this.methodNode(parentNode, edge.to, edge.kind);
      node.focused = true;
      const revisit = findAncestorMethod(revisitScopeNode, edge.to);
      if (revisit) {
        node.collapsedRevisit = true;
        node.revisitTargetKey = revisit.nodeKey;
      }
      return node;
    }

    crossModuleHop(methodNode, edge) {
      const node = this.methodNode(methodNode, edge.to, edge.kind);
      node.focused = true;
      node.collapsedCrossModule = true;
      node.targetModuleId = edge.toModuleId;
      return node;
    }
  }

  /** Walks a node's ancestor chain looking for the same method, the revisit rule. */
  function findAncestorMethod(node, methodId) {
    let current = node;
    while (current) {
      if (current.kind === NODE_KIND.METHOD && current.methodId === methodId) {
        return current;
      }
      current = current.parent;
    }
    return null;
  }

  function bootstrap(data) {
    const index = new CodemapIndex(data);
    const treeBuilder = new TreeBuilder(index);
    const view = new GraphView(index, treeBuilder);
    window.CodemapReport = { index, treeBuilder, view };
    view.render();
    wireControls(index, view);
  }

  /**
   * Owns D3 rendering of the lazy tree: layout, edge styling by kind, node
   * status outlines, revisit badges, and the side panel. Kept as one class
   * since selection, expansion, and drawing are tightly coupled through the
   * same tree state, but each concern lives in its own method.
   */
  class GraphView {
    constructor(index, treeBuilder) {
      this.index = index;
      this.treeBuilder = treeBuilder;
      this.svg = d3.select("#graph");
      this.viewport = this.svg.append("g").attr("class", "viewport");
      this.roots = treeBuilder.buildModuleRoots();
      this.selectedMethodId = null;
      this.focusOnChanges = false;
      this.searchTerm = "";
      this.layerFilter = "";
      this.moduleFilter = "";
      this.hasFittedView = false;
      this.setupZoom();
    }

    /**
     * Wires D3 zoom/pan and seeds its internal transform with the same
     * initial placement the viewport starts at, so the first pan or scroll
     * gesture adjusts from where the tree actually is rather than jumping
     * from an assumed identity transform.
     */
    setupZoom() {
      this.zoomBehavior = d3.zoom().scaleExtent([0.2, 3]).on("zoom", (event) => {
        this.viewport.attr("transform", event.transform);
      });
      this.svg.call(this.zoomBehavior);
      this.applyTransform(d3.zoomIdentity.translate(ROOT_MARGIN_X, ROOT_MARGIN_Y));
    }

    /** Applies a transform to both the viewport and the zoom behaviour's state. */
    applyTransform(transform) {
      this.viewport.attr("transform", transform);
      this.svg.call(this.zoomBehavior.transform, transform);
    }

    /**
     * Frames every laid-out node inside the current SVG viewport on first
     * render, so all module roots are visible regardless of how many there
     * are, rather than relying on the fixed initial margin alone.
     */
    fitToView(layoutNodes) {
      const svgNode = this.svg.node();
      if (!svgNode || typeof svgNode.getBoundingClientRect !== "function") {
        return;
      }
      const bounds = boundingBoxOf(layoutNodes);
      const viewportSize = svgNode.getBoundingClientRect();
      if (!viewportSize.width || !viewportSize.height) {
        return;
      }
      const scale = fittingScale(bounds, viewportSize);
      const translateX = ROOT_MARGIN_X - bounds.minX * scale;
      const translateY = ROOT_MARGIN_Y - bounds.minY * scale;
      this.applyTransform(d3.zoomIdentity.translate(translateX, translateY).scale(scale));
    }

    /**
     * The single click handler for every node: selecting (opening the side
     * panel) and expanding are not separate gestures here, because a reader
     * has no reason to want one without the other.
     *
     * <p>A CLASS node is the one exception: it is context, not a step in the
     * chain, so clicking it only opens its side panel and never touches the
     * canvas — see {@link TreeBuilder#expand}, which already makes expanding
     * a CLASS node a no-op; this keeps that same rule at the click layer so
     * a class node is never even attempted.
     */
    handleNodeClick(node) {
      this.select(node);
      if (node.kind !== NODE_KIND.CLASS) {
        this.toggle(node);
      }
    }

    toggle(node) {
      if (node.collapsedRevisit) {
        this.jumpTo(node.revisitTargetKey);
        return;
      }
      if (node.collapsedCrossModule) {
        this.jumpToModule(node);
        return;
      }
      if (node.expanded) {
        node.expanded = false;
        node.children = [];
      } else {
        this.treeBuilder.expand(node);
      }
      this.render();
    }

    /**
     * "Expanding" a collapsed cross-module callee (spec §3.2.2) does not
     * inline the target module's tree here — it takes the reader to that
     * module instead, by selecting the target method directly. The reader
     * lands in the target module's own call chain rather than a foreign
     * module's internals appearing inside this one's branch.
     */
    jumpToModule(node) {
      this.navigateToMethod(node.methodId);
    }

    jumpTo(nodeKey) {
      const target = this.findByKey(nodeKey);
      if (target) {
        this.flash(nodeKey);
      }
    }

    findByKey(nodeKey) {
      return this.findInTree((node) => node.nodeKey === nodeKey);
    }

    /**
     * Finds a method already materialised in the tree, wherever it occurs —
     * the "Called by"/"Calls" panel does not know which branch a target was
     * expanded under, so every root is searched.
     */
    findNodeByMethodId(methodId) {
      return this.findInTree((node) => node.kind === NODE_KIND.METHOD && node.methodId === methodId);
    }

    findInTree(predicate) {
      for (const root of this.roots) {
        const found = findInSubtree(root, predicate);
        if (found) {
          return found;
        }
      }
      return null;
    }

    flash(nodeKey) {
      this.viewport.selectAll("g.node").filter((d) => d.nodeKey === nodeKey)
          .select("circle")
          .transition().duration(TRANSITION_MS)
          .attr("r", NODE_RADIUS * 2)
          .transition().duration(TRANSITION_MS)
          .attr("r", NODE_RADIUS);
    }

    select(node) {
      this.selectedMethodId = node.methodId || null;
      this.render();
      this.renderSidePanel(node);
    }

    /**
     * Follows a "Called by"/"Calls" link to its target method (AC6): both
     * lists must be navigable, walking a chain upward from a repository to
     * the endpoints that reach it, or downward from an endpoint toward the
     * database.
     *
     * <p>The tree is lazy (spec §3.1), so the target may not exist as a node
     * yet — its ancestors may never have been expanded. Rather than fail
     * silently, this selects a synthetic method node when no materialised one
     * is found, so the side panel always opens; the reader can still expand
     * the real tree from there to see the target in context.
     */
    navigateToMethod(methodId) {
      const existing = this.findNodeByMethodId(methodId);
      const node = existing || makeTreeNode(NODE_KIND.METHOD, methodId, methodId, null, { methodId });
      this.select(node);
      if (existing) {
        this.flash(existing.nodeKey);
      }
    }

    setFocusOnChanges(value) {
      this.focusOnChanges = value;
      this.render();
    }

    setSearchTerm(term) {
      this.searchTerm = (term || "").toLowerCase();
      this.render();
    }

    setLayerFilter(layer) {
      this.layerFilter = layer || "";
      this.render();
    }

    setModuleFilter(moduleId) {
      this.moduleFilter = moduleId || "";
      this.render();
    }

    render() {
      const visibleRoots = this.visibleRoots();
      const layoutNodes = [];
      const layoutLinks = [];
      layoutTree(visibleRoots, layoutNodes, layoutLinks);
      this.drawLinks(layoutLinks);
      this.drawNodes(layoutNodes);
      if (!this.hasFittedView && layoutNodes.length > 0) {
        this.fitToView(layoutNodes);
        this.hasFittedView = true;
      }
    }

    /**
     * The roots actually drawn this render: module filter, then focus-on-
     * changes, then search/layer filtering, applied in that order because
     * each narrows what the next stage needs to consider.
     *
     * <p>Search and layer filtering prune a *view* of the tree (see
     * {@link pruneByFilters}) rather than the tree itself, so an ancestor
     * whose own label does not match still renders when one of its already-
     * expanded descendants does — hiding a node must not orphan children the
     * reader already opened, and a match has to stay reachable from a root.
     */
    visibleRoots() {
      const moduleFiltered = filterModuleRoots(this.roots, this.moduleFilter);
      const changeFiltered = this.focusOnChanges
          ? moduleFiltered.filter((root) => subtreeHasChange(root))
          : moduleFiltered;
      if (!this.searchTerm && !this.layerFilter) {
        return changeFiltered;
      }
      return pruneByFilters(changeFiltered, (node) => this.matchesFilters(node));
    }

    matchesFilters(node) {
      if (node.kind !== NODE_KIND.METHOD && node.kind !== NODE_KIND.ENTRY_POINT) {
        return true;
      }
      if (this.searchTerm && !nodeMatchesSearch(node, this.index, this.searchTerm)) {
        return false;
      }
      if (this.layerFilter && !nodeMatchesLayer(node, this.index, this.layerFilter)) {
        return false;
      }
      return true;
    }

    drawLinks(layoutLinks) {
      const selection = this.viewport.selectAll("path.link")
          .data(layoutLinks, (d) => d.target.nodeKey);
      selection.exit().remove();
      selection.enter()
          .append("path")
          .attr("class", (d) => "link " + linkClass(d.kind))
          .merge(selection)
          .attr("class", (d) => "link " + linkClass(d.kind))
          .attr("d", (d) => treeLink(d.source, d.target));
    }

    drawNodes(layoutNodes) {
      const selection = this.viewport.selectAll("g.node")
          .data(layoutNodes, (d) => d.nodeKey);
      selection.exit().remove();

      const entered = selection.enter().append("g").attr("class", "node");
      entered.append("circle").attr("r", NODE_RADIUS);
      entered.append("text").attr("dy", 4).attr("x", NODE_RADIUS + 4);

      const merged = entered.merge(selection);
      merged
          .attr("class", (d) => nodeClass(d, this.selectedMethodId))
          .attr("transform", (d) => "translate(" + d.x + "," + d.y + ")")
          .on("click", (event, d) => this.handleNodeClick(d));

      merged.select("text").text((d) => nodeLabel(d));
    }

    renderSidePanel(node) {
      const panel = document.getElementById("side-panel");
      panel.innerHTML = "";
      const onNavigate = (methodId) => this.navigateToMethod(methodId);
      if (node.kind === NODE_KIND.METHOD) {
        panel.appendChild(buildMethodPanel(this.index, node, onNavigate));
      } else if (node.kind === NODE_KIND.CLASS) {
        panel.appendChild(buildClassPanel(this.index, node, onNavigate));
      } else {
        const placeholder = document.createElement("div");
        placeholder.className = "placeholder";
        placeholder.textContent = "Select a method to see details.";
        panel.appendChild(placeholder);
      }
    }
  }

  function findInSubtree(node, predicate) {
    if (!node) {
      return null;
    }
    if (predicate(node)) {
      return node;
    }
    for (const child of node.children) {
      const found = findInSubtree(child, predicate);
      if (found) {
        return found;
      }
    }
    return null;
  }

  function subtreeHasChange(node) {
    const changed = node.status && node.status !== CHANGE_STATUS.UNCHANGED;
    if (changed) {
      return true;
    }
    return node.children.some((child) => subtreeHasChange(child));
  }

  /** Keeps only the module root matching `moduleId`, or every root when none is chosen. */
  function filterModuleRoots(roots, moduleId) {
    if (!moduleId) {
      return roots;
    }
    return roots.filter((root) => root.id === moduleId);
  }

  /**
   * Builds a filtered *view* of a tree: a node survives if it matches
   * `predicate` itself, or if any of its already-materialised descendants do.
   * Children that survive are copied into a shallow clone of their parent, so
   * the real tree (and its `expanded`/`children` state used for further lazy
   * expansion) is never mutated by filtering.
   *
   * @param roots the roots to filter (already past the module/change filters)
   * @param predicate called with a node, true when it matches the active filters
   * @return a new array of root clones, containing only matching branches
   */
  function pruneByFilters(roots, predicate) {
    return roots.map((root) => pruneNode(root, predicate)).filter((node) => node !== null);
  }

  function pruneNode(node, predicate) {
    const survivingChildren = node.children
        .map((child) => (child ? pruneNode(child, predicate) : null))
        .filter((child) => child !== null);
    // A CLASS node is context for the method(s) beneath it, not a filterable
    // leaf in its own right (spec §3.2.1) — it survives only when at least
    // one of its methods does, never on its own label matching.
    const survivesOnItsOwnMerit = node.kind !== NODE_KIND.CLASS && predicate(node);
    if (survivingChildren.length === 0 && !survivesOnItsOwnMerit) {
      return null;
    }
    return Object.assign({}, node, { children: survivingChildren });
  }

  function nodeMatchesSearch(node, index, term) {
    if (node.label.toLowerCase().includes(term)) {
      return true;
    }
    if (node.methodId) {
      const method = index.method(node.methodId);
      const owner = method && index.classOf(method.classId);
      return owner ? owner.simpleName.toLowerCase().includes(term) : false;
    }
    return false;
  }

  function nodeMatchesLayer(node, index, layer) {
    if (!node.methodId) {
      return true;
    }
    const method = index.method(node.methodId);
    const owner = method && index.classOf(method.classId);
    return owner ? owner.layer === layer : true;
  }

  function linkClass(kind) {
    switch (kind) {
      case EDGE_KIND.CALL_INTERNAL:
        return "link-call-internal";
      case EDGE_KIND.CALL_EXTERNAL:
        return "link-call-external";
      case EDGE_KIND.CROSS_MODULE:
        return "link-cross-module";
      case EDGE_KIND.IMPLEMENTS:
        return "link-implements";
      case EDGE_KIND.USES_TYPE:
        return "link-uses-type";
      default:
        // No real call edge: this is the class-node-to-its-one-method
        // connector (entry point -> class -> handler, or a cross-class call
        // -> class -> callee), not a call in its own right.
        return "link-class-member";
    }
  }

  function nodeClass(node, selectedMethodId) {
    const classes = ["node"];
    if (node.collapsedRevisit) {
      classes.push("node-revisit");
    }
    if (node.focused) {
      classes.push("node-focused");
    }
    if (node.status === CHANGE_STATUS.ADDED || node.status === CHANGE_STATUS.CHANGED) {
      classes.push("node-changed");
    } else if (node.status === CHANGE_STATUS.REMOVED) {
      classes.push("node-removed");
    } else if (node.status === CHANGE_STATUS.AFFECTED) {
      classes.push("node-affected");
    }
    if (node.methodId && node.methodId === selectedMethodId) {
      classes.push("selected");
    }
    return classes.join(" ");
  }

  function nodeLabel(node) {
    return node.collapsedRevisit ? node.label + " " + ALREADY_ABOVE_LABEL : node.label;
  }

  /**
   * Lays out every root's subtree left-to-right: depth advances the along-axis
   * (x, screen-horizontal) and siblings stack along the cross-axis (y,
   * screen-vertical), spaced no closer than {@link MIN_SIBLING_SPACING}. A
   * margin keeps the first root off the SVG's edge instead of clipped at 0,0.
   *
   * @param roots the tree's root nodes (module roots)
   * @param outNodes every laid-out node is appended here
   * @param outLinks every parent-child link is appended here, as {source, target, kind}
   */
  function layoutTree(roots, outNodes, outLinks) {
    let cursorCrossAxis = ROOT_MARGIN_Y;
    for (const root of roots) {
      cursorCrossAxis = layoutSubtree(root, 0, cursorCrossAxis, outNodes, outLinks) + MIN_SIBLING_SPACING;
    }
  }

  function layoutSubtree(node, depth, crossAxisOffset, outNodes, outLinks) {
    node.depth = depth;
    node.alongAxis = ROOT_MARGIN_X + depth * LEVEL_WIDTH;
    if (node.children.length === 0 || node.collapsedRevisit) {
      node.crossAxis = crossAxisOffset;
      applyScreenCoordinates(node);
      outNodes.push(node);
      return crossAxisOffset + MIN_SIBLING_SPACING;
    }
    let cursor = crossAxisOffset;
    const childCrossAxes = [];
    for (const child of node.children) {
      if (!child) {
        continue;
      }
      cursor = layoutSubtree(child, depth + 1, cursor, outNodes, outLinks);
      childCrossAxes.push(child.crossAxis);
      outLinks.push({ source: node, target: child, kind: child.edgeKind });
    }
    node.crossAxis = childCrossAxes.length > 0 ? average(childCrossAxes) : crossAxisOffset;
    applyScreenCoordinates(node);
    outNodes.push(node);
    return cursor;
  }

  /** Maps the depth/sibling layout axes onto screen coordinates (x, y). */
  function applyScreenCoordinates(node) {
    node.x = node.alongAxis;
    node.y = node.crossAxis;
  }

  function average(numbers) {
    return numbers.reduce((sum, n) => sum + n, 0) / numbers.length;
  }

  /** A horizontal bezier connecting a parent to a child laid out to its right. */
  /**
   * A bezier from just past the source's label to the target's circle.
   *
   * <p>Edges leave from {@link LABEL_CLEARANCE} to the right of the node rather
   * than from its centre: a label sits immediately right of its circle, so a
   * parent with many children would otherwise have its own name buried under
   * the fan of curves leaving it — which is exactly the case for a module root
   * with twenty entry points.
   */
  function treeLink(source, target) {
    const departureX = source.x + LABEL_CLEARANCE;
    const midX = (departureX + target.x) / 2;
    return "M" + departureX + "," + source.y
        + "C" + midX + "," + source.y
        + " " + midX + "," + target.y
        + " " + target.x + "," + target.y;
  }

  /** The smallest axis-aligned box containing every laid-out node. */
  function boundingBoxOf(layoutNodes) {
    const xs = layoutNodes.map((n) => n.x);
    const ys = layoutNodes.map((n) => n.y);
    return {
      minX: Math.min(...xs),
      maxX: Math.max(...xs),
      minY: Math.min(...ys),
      maxY: Math.max(...ys)
    };
  }

  /** The largest scale that keeps a bounding box inside the given viewport, capped at 1. */
  function fittingScale(bounds, viewportSize) {
    const contentWidth = Math.max(bounds.maxX - bounds.minX, 1);
    const contentHeight = Math.max(bounds.maxY - bounds.minY, 1);
    const scaleX = (viewportSize.width - ROOT_MARGIN_X * 2) / contentWidth;
    const scaleY = (viewportSize.height - ROOT_MARGIN_Y * 2) / contentHeight;
    return Math.min(1, scaleX, scaleY);
  }

  function buildMethodPanel(index, node, onNavigate) {
    const fragment = document.createDocumentFragment();
    const method = index.method(node.methodId);
    const owner = index.classOf(method.classId);
    const module = owner ? index.module(owner.moduleId) : null;

    fragment.appendChild(section("Signature", textElement("code", method.signature)));
    fragment.appendChild(badgeSection(owner, module));
    fragment.appendChild(section("Location", textElement("div",
        method.file + ":" + method.lineStart + "-" + method.lineEnd)));
    if (method.javadoc) {
      fragment.appendChild(section("Javadoc", textElement("div", method.javadoc)));
    }
    fragment.appendChild(navSection("Called by", index.incoming(method.id), index, "from", onNavigate));
    fragment.appendChild(navSection("Calls", index.outgoing(method.id), index, "to", onNavigate));
    fragment.appendChild(section("Source", sourceElement(method)));
    return fragment;
  }

  /**
   * Projects a class into exactly what its side panel shows (spec §3.2.1):
   * name, file, layer/module, Javadoc, and the methods it declares.
   *
   * <p>Deliberately excludes the class's full file source. `MethodView`
   * already embeds every method's real body; re-embedding the surrounding
   * file text a second time would roughly double the source payload for no
   * new information (class-declared line spans run ~1.7x method-declared
   * spans, measured on Kairos) — the method list here is clickable, so a
   * reader reaches any of those bodies in one more step instead.
   *
   * @return {null} when the class id is unknown
   */
  function buildClassPanelData(index, classId) {
    const owningClass = index.classOf(classId);
    if (!owningClass) {
      return null;
    }
    const module = index.module(owningClass.moduleId);
    return {
      classId,
      simpleName: owningClass.simpleName,
      fqn: owningClass.fqn,
      file: owningClass.file,
      layer: owningClass.layer,
      moduleName: module ? module.name : owningClass.moduleId,
      javadoc: owningClass.javadoc,
      methods: index.methodsOfClass(classId)
    };
  }

  function buildClassPanel(index, node, onNavigate) {
    const fragment = document.createDocumentFragment();
    const panelData = buildClassPanelData(index, node.classId);
    if (!panelData) {
      return fragment;
    }

    fragment.appendChild(section("Class", textElement("code", panelData.fqn)));
    fragment.appendChild(classBadgeSection(panelData));
    fragment.appendChild(section("File", textElement("div", panelData.file)));
    if (panelData.javadoc) {
      fragment.appendChild(section("Javadoc", textElement("div", panelData.javadoc)));
    }
    fragment.appendChild(section("Methods", classMethodList(panelData.methods, onNavigate)));
    return fragment;
  }

  function classBadgeSection(panelData) {
    const wrapper = document.createElement("div");
    wrapper.className = "panel-section";
    wrapper.appendChild(badge("badge-layer", panelData.layer));
    wrapper.appendChild(badge("badge-module", panelData.moduleName));
    return wrapper;
  }

  /** The class's declared methods, so a reader sees what else is in there. */
  function classMethodList(methods, onNavigate) {
    const list = document.createElement("ul");
    list.className = "nav-list";
    for (const method of methods) {
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.textContent = method.signature;
      link.dataset.methodId = method.id;
      link.addEventListener("click", () => onNavigate(method.id));
      item.appendChild(link);
      list.appendChild(item);
    }
    return list;
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

  function badgeSection(ownerClass, module) {
    const wrapper = document.createElement("div");
    wrapper.className = "panel-section";
    if (ownerClass) {
      wrapper.appendChild(badge("badge-layer", ownerClass.layer));
    }
    if (module) {
      wrapper.appendChild(badge("badge-module", module.name));
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

  function sourceElement(method) {
    const element = textElement("pre", method.source);
    element.className = SOURCE_CLASS;
    return element;
  }

  /**
   * A "Called by"/"Calls" list (spec AC6): every entry is a real navigation
   * target, not just a label — clicking it walks the chain to that method,
   * revealing it in the tree (or opening its panel directly, when it is not
   * yet materialised) so a reader can walk upward to a caller or downward
   * toward the database without leaving the panel.
   */
  function navSection(title, edges, index, targetKey, onNavigate) {
    const list = document.createElement("ul");
    list.className = "nav-list";
    for (const edge of edges) {
      const methodId = edge[targetKey];
      const method = index.method(methodId);
      if (!method) {
        continue;
      }
      const owner = index.classOf(method.classId);
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.textContent = (owner ? owner.simpleName + "." : "") + method.name;
      link.dataset.methodId = methodId;
      link.addEventListener("click", () => onNavigate(methodId));
      item.appendChild(link);
      list.appendChild(item);
    }
    return section(title, list);
  }

  function wireControls(index, view) {
    const searchInput = document.getElementById("search-input");
    const layerFilter = document.getElementById("layer-filter");
    const moduleFilter = document.getElementById("module-filter");
    const focusButton = document.getElementById("focus-changes-button");

    populateLayerOptions(layerFilter, index);
    populateModuleOptions(moduleFilter, index);

    searchInput.addEventListener("input", (event) => view.setSearchTerm(event.target.value));
    layerFilter.addEventListener("change", (event) => view.setLayerFilter(event.target.value));
    moduleFilter.addEventListener("change", (event) => view.setModuleFilter(event.target.value));
    focusButton.addEventListener("click", () => {
      const active = focusButton.classList.toggle("active");
      view.setFocusOnChanges(active);
    });

    wireModuleOverview(index);
    wireThemeToggle();
  }

  const MODULE_OVERVIEW_BUTTON_ID = "module-overview-button";
  const MODULE_OVERVIEW_PANEL_ID = "module-overview-panel";
  const MODULE_OVERVIEW_VISIBLE_CLASS = "visible";

  /**
   * Adds the module-overview control (spec §3.2.2): a header toggle button
   * and a panel listing "which module depends on which", built from the DOM
   * rather than the static template — the overview is optional chrome the
   * base page does not need to declare a slot for.
   */
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

  const THEME_STORAGE_KEY = "codemap-theme";
  const THEME_ATTRIBUTE = "data-theme";
  const THEME_LIGHT = "light";
  const THEME_DARK = "dark";

  /**
   * Adds the light/dark theme toggle. The choice is applied as an attribute
   * on the document root, so every colour switches through the CSS custom
   * properties in report.css rather than any rule being rewritten from JS.
   *
   * <p>Persistence via `localStorage` is a nice-to-have, not a requirement —
   * `file://` pages do have a working `localStorage`, but a future embedding
   * context (e.g. a sandboxed iframe) might not, so a failure here degrades
   * silently instead of breaking the toggle itself.
   */
  function wireThemeToggle() {
    const header = document.querySelector("header .controls");
    const button = document.createElement("button");
    button.id = "theme-toggle-button";
    button.type = "button";
    header.appendChild(button);

    const root = document.documentElement;
    applyTheme(initialTheme(browserThemeEnvironment()), button, root);
    button.addEventListener("click", () => {
      const next = root.getAttribute(THEME_ATTRIBUTE) === THEME_DARK ? THEME_LIGHT : THEME_DARK;
      applyTheme(next, button, root);
      persistTheme(next);
    });
  }

  /** The real `localStorage`/`matchMedia` lookups, isolated so the decision logic can be tested without a DOM. */
  function browserThemeEnvironment() {
    return { getStoredTheme: readStoredTheme, prefersLight: prefersLightColorScheme };
  }

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

  /**
   * Projects the raw `moduleDependencies` pairs into overview rows with real
   * module names (spec §3.2.2, §3.5) — "which module depends on which",
   * resolved from ids to something a reader recognises without cross-
   * referencing the module list by hand.
   *
   * @return {Array<{fromModuleId, toModuleId, fromName, toName}>} one row per
   *         aggregated CROSS_MODULE dependency; a module with no outgoing
   *         cross-module call contributes no row of its own
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

  /**
   * Renders the module overview as a simple dependency list — "X depends on
   * Y" per aggregated CROSS_MODULE relationship. This is deliberately not an
   * elaborate diagram: the issue calls it "often the first thing a reader
   * wants", so a plain, always-correct list beats a fancier rendering that
   * risks drifting from the real edges.
   */
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

  window.CodemapInternal = {
    NODE_KIND,
    EDGE_KIND,
    CHANGE_STATUS,
    ALREADY_ABOVE_LABEL,
    ROOT_PAGE_LABEL,
    NODE_RADIUS,
    LEVEL_WIDTH,
    MIN_SIBLING_SPACING,
    ROOT_MARGIN_X,
    ROOT_MARGIN_Y,
    TRANSITION_MS,
    CodemapIndex,
    TreeBuilder,
    GraphView,
    makeTreeNode,
    findAncestorMethod,
    layoutTree,
    boundingBoxOf,
    fittingScale,
    filterModuleRoots,
    pruneByFilters,
    nodeMatchesSearch,
    nodeMatchesLayer,
    buildModuleOverview,
    THEME_LIGHT,
    THEME_DARK,
    THEME_ATTRIBUTE,
    THEME_STORAGE_KEY,
    initialTheme,
    applyTheme,
    buildClassPanelData
  };

  if (DATA) {
    bootstrap(DATA);
  }
})();
