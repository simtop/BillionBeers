package com.simtop.billionbeers.buildlogic

import groovy.json.JsonOutput
import java.nio.charset.StandardCharsets
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateModuleGraphTask : DefaultTask() {

  @get:Input abstract val rootProjectName: Property<String>

  @get:Input abstract val nodeRecords: ListProperty<String>

  @get:Input abstract val edgeRecords: ListProperty<String>

  @get:Input abstract val includedBuildNames: ListProperty<String>

  @get:OutputFile abstract val jsonOutput: RegularFileProperty

  @get:OutputFile abstract val htmlOutput: RegularFileProperty

  @TaskAction
  fun generate() {
    val nodes =
      nodeRecords.get().map(::parseNode).sortedBy { it.path }
    val edges =
      edgeRecords
        .get()
        .map(::parseEdge)
        .groupBy { it.source to it.target }
        .map { (pair, declarations) ->
          ModuleEdge(
            source = pair.first,
            target = pair.second,
            configurations = declarations.map { it.configuration }.distinct().sorted(),
            scopes = declarations.map { it.scope }.distinct().sortedBy(::scopeOrder),
          )
        }
        .sortedWith(compareBy(ModuleEdge::source, ModuleEdge::target))
    val cycles = stronglyConnectedComponents(nodes.map { it.path }, edges)
    val fanIn = edges.groupingBy { it.target }.eachCount()
    val fanOut = edges.groupingBy { it.source }.eachCount()
    val apiProjectEdgeCount = edges.count { edge ->
      edge.configurations.any { it.equals("api", ignoreCase = true) }
    }
    val model =
      linkedMapOf<String, Any>(
        "schemaVersion" to 1,
        "rootProject" to linkedMapOf("name" to rootProjectName.get()),
        "includedBuilds" to
          includedBuildNames.get().distinct().sorted().map { name ->
            linkedMapOf(
              "name" to name,
              "internalProjectsIncluded" to false,
            )
          },
        "nodes" to
          nodes.map { node ->
            linkedMapOf(
              "path" to node.path,
              "name" to node.name,
              "relativeDirectory" to node.relativeDirectory,
              "kind" to node.kind,
            )
          },
        "edges" to
          edges.map { edge ->
            linkedMapOf(
              "source" to edge.source,
              "target" to edge.target,
              "configurations" to edge.configurations,
              "scopes" to edge.scopes,
            )
          },
        "cycles" to cycles.map { modules -> linkedMapOf("modules" to modules) },
        "summary" to
          linkedMapOf(
            "nodeCount" to nodes.size,
            "edgeCount" to edges.size,
            "cycleCount" to cycles.size,
            "maxFanIn" to (fanIn.values.maxOrNull() ?: 0),
            "maxFanOut" to (fanOut.values.maxOrNull() ?: 0),
            "apiProjectEdgeCount" to apiProjectEdgeCount,
          ),
      )
    val json = JsonOutput.prettyPrint(JsonOutput.toJson(model)) + "\n"

    jsonOutput.get().asFile.apply {
      parentFile.mkdirs()
      writeText(json)
    }
    htmlOutput.get().asFile.apply {
      parentFile.mkdirs()
      writeText(renderHtml(json))
    }
  }

  private fun parseNode(record: String): ModuleNode {
    val parts = record.split(RECORD_SEPARATOR)
    require(parts.size == 4) { "Invalid module node record: $record" }
    return ModuleNode(
      path = parts[0],
      name = parts[1],
      relativeDirectory = parts[2],
      kind = parts[3],
    )
  }

  private fun parseEdge(record: String): EdgeDeclaration {
    val parts = record.split(RECORD_SEPARATOR)
    require(parts.size == 4) { "Invalid module edge record: $record" }
    return EdgeDeclaration(
      source = parts[0],
      target = parts[1],
      configuration = parts[2],
      scope = parts[3],
    )
  }

  private fun scopeOrder(scope: String): Int =
    when (scope) {
      "main" -> 0
      "test" -> 1
      "androidTest" -> 2
      "benchmark" -> 3
      else -> 4
    }

  private fun stronglyConnectedComponents(
    nodePaths: List<String>,
    edges: List<ModuleEdge>,
  ): List<List<String>> {
    val outgoing =
      nodePaths.associateWith { mutableListOf<String>() }.toMutableMap().apply {
        edges.forEach { edge -> getOrPut(edge.source, ::mutableListOf).add(edge.target) }
        values.forEach { it.sort() }
      }
    var nextIndex = 0
    val indices = mutableMapOf<String, Int>()
    val lowLinks = mutableMapOf<String, Int>()
    val stack = ArrayDeque<String>()
    val onStack = mutableSetOf<String>()
    val components = mutableListOf<List<String>>()

    fun visit(node: String) {
      indices[node] = nextIndex
      lowLinks[node] = nextIndex
      nextIndex += 1
      stack.addLast(node)
      onStack += node

      outgoing[node].orEmpty().forEach { target ->
        if (target !in indices) {
          visit(target)
          lowLinks[node] = minOf(lowLinks.getValue(node), lowLinks.getValue(target))
        } else if (target in onStack) {
          lowLinks[node] = minOf(lowLinks.getValue(node), indices.getValue(target))
        }
      }

      if (lowLinks.getValue(node) == indices.getValue(node)) {
        val component = mutableListOf<String>()
        do {
          val member = stack.removeLast()
          onStack -= member
          component += member
        } while (member != node)
        component.sort()
        val isSelfCycle =
          component.size == 1 && edges.any { it.source == component.single() && it.target == component.single() }
        if (component.size > 1 || isSelfCycle) components += component
      }
    }

    nodePaths.sorted().forEach { node -> if (node !in indices) visit(node) }
    return components.sortedBy { it.joinToString(RECORD_SEPARATOR) }
  }

  private fun renderHtml(json: String): String {
    val cytoscape = loadResource(CYTOSCAPE_RESOURCE)
    val cytoscapeLicense = loadResource(CYTOSCAPE_LICENSE_RESOURCE).replace("--", "—")
    val jsonForHtml = json.replace("</", "<\\/")
    return """
      <!doctype html>
      <!--
      Cytoscape.js 3.34.1 license notice:
      $cytoscapeLicense
      -->
      <html lang="en">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Gradle Module Graph</title>
        <style>
          :root {
            color-scheme: light dark;
            --bg: #f5f7fb;
            --panel: #ffffff;
            --text: #182033;
            --muted: #667085;
            --border: #d7dce5;
            --accent: #7357d9;
            --dependency: #e69f00;
            --dependent: #2a9d8f;
            --transitive-dependency: #f4c95d;
            --transitive-dependent: #77c8bc;
            --danger: #c23b53;
          }
          @media (prefers-color-scheme: dark) {
            :root {
              --bg: #111522;
              --panel: #1b2132;
              --text: #eef1f8;
              --muted: #a9b1c4;
              --border: #343d52;
              --accent: #a58bf4;
              --dependency: #ffbd4a;
              --dependent: #55c9b8;
              --transitive-dependency: #ae8334;
              --transitive-dependent: #3d8279;
              --danger: #f06b82;
            }
          }
          * { box-sizing: border-box; }
          body { margin: 0; background: var(--bg); color: var(--text); font-family: Inter, ui-sans-serif, system-ui, sans-serif; overflow: hidden; }
          .app { display: grid; grid-template-columns: minmax(17rem, 22rem) 1fr minmax(18rem, 24rem); height: 100vh; }
          .panel { background: var(--panel); border-right: 1px solid var(--border); padding: 1rem; overflow: auto; }
          .details { border-right: 0; border-left: 1px solid var(--border); }
          h1 { margin: 0 0 .25rem; font-size: 1.2rem; }
          h2 { margin: 1.25rem 0 .5rem; font-size: .92rem; }
          p, li, label, button, input { font-size: .82rem; }
          .muted { color: var(--muted); }
          .summary { margin: .25rem 0 1rem; }
          input[type="search"] { width: 100%; padding: .65rem .7rem; border: 1px solid var(--border); border-radius: .5rem; background: var(--bg); color: var(--text); }
          .buttons { display: grid; grid-template-columns: 1fr 1fr; gap: .5rem; margin: .65rem 0 1rem; }
          button { padding: .55rem; border: 1px solid var(--border); border-radius: .5rem; background: var(--panel); color: var(--text); cursor: pointer; }
          button:hover { border-color: var(--accent); }
          .toggle { display: flex; gap: .5rem; align-items: center; margin: .55rem 0; }
          .scopes { display: grid; grid-template-columns: 1fr 1fr; gap: .3rem; }
          .legend { display: grid; gap: .35rem; }
          .legend span { display: flex; align-items: center; gap: .45rem; }
          .swatch { width: .75rem; height: .75rem; border-radius: 50%; flex: 0 0 auto; }
          #cy { width: 100%; height: 100vh; background: var(--bg); }
          #cycle-warning { border: 1px solid var(--danger); color: var(--danger); border-radius: .5rem; padding: .65rem; margin-top: 1rem; }
          #cycle-warning[hidden] { display: none; }
          .module-path { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
          .edge-list { list-style: none; margin: 0; padding: 0; }
          .edge-list li { border-top: 1px solid var(--border); padding: .55rem 0; }
          .pill { display: inline-block; margin: .15rem .15rem 0 0; padding: .12rem .35rem; border-radius: 99px; border: 1px solid var(--border); color: var(--muted); font-size: .72rem; }
          .license { margin-top: 1.5rem; padding-top: .75rem; border-top: 1px solid var(--border); font-size: .7rem; }
          @media (max-width: 900px) {
            .app { grid-template-columns: minmax(15rem, 19rem) 1fr; }
            .details { display: none; }
          }
        </style>
      </head>
      <body>
        <div class="app">
          <aside class="panel controls">
            <h1>Gradle Module Graph</h1>
            <p id="summary" class="summary muted"></p>
            <input id="module-search" type="search" list="module-options" placeholder="Find :feature:beerslist…" aria-label="Find module">
            <datalist id="module-options"></datalist>
            <div class="buttons">
              <button id="select-module">Select</button>
              <button id="fit-graph">Fit graph</button>
              <button id="reset-graph">Reset</button>
              <button id="clear-selection">Clear</button>
            </div>
            <label class="toggle"><input id="transitive" type="checkbox"> Include transitive neighborhood</label>
            <label class="toggle"><input id="focus" type="checkbox"> Focus selected neighborhood</label>
            <h2>Edge scopes</h2>
            <div id="scope-filters" class="scopes"></div>
            <h2>Module kinds</h2>
            <div id="kind-legend" class="legend"></div>
            <div id="cycle-warning" hidden></div>
            <p class="license muted">Cytoscape.js 3.34.1 · Copyright Cytoscape.js contributors · MIT License. The viewer is embedded for offline use.</p>
          </aside>
          <main id="cy" aria-label="Interactive module dependency graph"></main>
          <aside class="panel details">
            <h1>Selection</h1>
            <div id="details" class="muted">Select a module or edge to inspect it.</div>
          </aside>
        </div>
        <script id="graph-data" type="application/json">$jsonForHtml</script>
        <script>
          $cytoscape
        </script>
        <script>
          (function () {
            'use strict';
            const model = JSON.parse(document.getElementById('graph-data').textContent);
            const scopeOrder = ['main', 'test', 'androidTest', 'benchmark', 'tooling/other'];
            const kindColors = {
              'android-application': '#7357d9',
              'android-dynamic-feature': '#c23b53',
              'android-library': '#3b82c4',
              'android-test': '#d47b20',
              'jvm-library': '#2a9d8f',
              'other': '#7c879d'
            };
            const elements = [];
            model.nodes.forEach(function (node) {
              elements.push({ data: { id: node.path, label: node.path, kind: node.kind, directory: node.relativeDirectory } });
            });
            model.edges.forEach(function (edge, index) {
              elements.push({ data: {
                id: 'edge-' + index,
                source: edge.source,
                target: edge.target,
                configurations: edge.configurations.join(', '),
                scopes: edge.scopes.join(','),
                scopeList: edge.scopes
              }});
            });
            const cy = cytoscape({
              container: document.getElementById('cy'),
              elements: elements,
              minZoom: 0.08,
              maxZoom: 4,
              wheelSensitivity: 0.2,
              style: [
                { selector: 'node', style: {
                  'background-color': function (ele) { return kindColors[ele.data('kind')] || kindColors.other; },
                  'label': 'data(label)', 'font-size': 9, 'color': getComputedStyle(document.documentElement).getPropertyValue('--text').trim(),
                  'text-wrap': 'wrap', 'text-max-width': 115, 'text-valign': 'bottom', 'text-margin-y': 7,
                  'width': 23, 'height': 23, 'border-width': 2, 'border-color': '#ffffff'
                }},
                { selector: 'edge', style: {
                  'curve-style': 'bezier', 'width': 1.4, 'line-color': '#9aa4b5',
                  'target-arrow-color': '#9aa4b5', 'target-arrow-shape': 'triangle', 'arrow-scale': 0.75,
                  'opacity': 0.58
                }},
                { selector: '.selected-node', style: { 'border-width': 5, 'border-color': '#ffffff', 'width': 34, 'height': 34, 'z-index': 20 }},
                { selector: '.dependency', style: { 'background-color': '#e69f00', 'width': 29, 'height': 29 }},
                { selector: '.dependent', style: { 'background-color': '#2a9d8f', 'width': 29, 'height': 29 }},
                { selector: '.transitive-dependency', style: { 'background-color': '#f4c95d' }},
                { selector: '.transitive-dependent', style: { 'background-color': '#77c8bc' }},
                { selector: '.highlight-edge', style: { 'width': 3, 'opacity': 1, 'line-color': '#7357d9', 'target-arrow-color': '#7357d9' }},
                { selector: '.dimmed', style: { 'opacity': 0.08 }},
                { selector: '.hidden-by-filter', style: { 'display': 'none' }},
                { selector: '.focused-out', style: { 'display': 'none' }}
              ],
              layout: { name: 'breadthfirst', directed: true, padding: 36, spacingFactor: 1.25, animate: false }
            });

            const search = document.getElementById('module-search');
            const details = document.getElementById('details');
            const transitive = document.getElementById('transitive');
            const focus = document.getElementById('focus');
            const allScopes = Array.from(new Set(model.edges.flatMap(function (edge) { return edge.scopes; })));
            let selectedModule = null;

            document.getElementById('summary').textContent = model.summary.nodeCount + ' modules · ' + model.summary.edgeCount + ' dependency edges';
            const options = document.getElementById('module-options');
            model.nodes.forEach(function (node) {
              const option = document.createElement('option');
              option.value = node.path;
              options.appendChild(option);
            });

            const filters = document.getElementById('scope-filters');
            scopeOrder.filter(function (scope) { return allScopes.includes(scope); }).forEach(function (scope) {
              const label = document.createElement('label');
              label.className = 'toggle';
              const input = document.createElement('input');
              input.type = 'checkbox';
              input.value = scope;
              input.checked = scope === 'main';
              input.addEventListener('change', applyFilters);
              label.appendChild(input);
              label.appendChild(document.createTextNode(scope));
              filters.appendChild(label);
            });

            const legend = document.getElementById('kind-legend');
            Array.from(new Set(model.nodes.map(function (node) { return node.kind; }))).sort().forEach(function (kind) {
              const row = document.createElement('span');
              const swatch = document.createElement('i');
              swatch.className = 'swatch';
              swatch.style.background = kindColors[kind] || kindColors.other;
              row.appendChild(swatch);
              row.appendChild(document.createTextNode(kind));
              legend.appendChild(row);
            });

            const cycleWarning = document.getElementById('cycle-warning');
            if (model.cycles.length) {
              cycleWarning.hidden = false;
              cycleWarning.textContent = model.cycles.length + ' strongly connected component(s): ' +
                model.cycles.map(function (cycle) { return cycle.modules.join(' ↔ '); }).join(' · ');
            }

            function enabledScopes() {
              return Array.from(filters.querySelectorAll('input:checked')).map(function (input) { return input.value; });
            }

            function applyFilters() {
              const enabled = enabledScopes();
              cy.edges().forEach(function (edge) {
                const visible = edge.data('scopeList').some(function (scope) { return enabled.includes(scope); });
                edge.toggleClass('hidden-by-filter', !visible);
              });
              cy.nodes().removeClass('hidden-by-filter');
              cy.nodes().forEach(function (node) {
                const hasVisibleEdge = node.connectedEdges().some(function (edge) { return !edge.hasClass('hidden-by-filter'); });
                node.toggleClass('hidden-by-filter', model.nodes.length > 1 && !hasVisibleEdge && selectedModule !== node.id());
              });
              highlightSelection(false);
            }

            function visibleOutgoing(node) {
              return node.outgoers('edge').filter(function (edge) { return !edge.hasClass('hidden-by-filter'); });
            }

            function visibleIncoming(node) {
              return node.incomers('edge').filter(function (edge) { return !edge.hasClass('hidden-by-filter'); });
            }

            function traverse(start, direction) {
              const foundNodes = cy.collection();
              const foundEdges = cy.collection();
              const queue = [start];
              const visited = new Set([start.id()]);
              while (queue.length) {
                const current = queue.shift();
                const edges = direction === 'out' ? visibleOutgoing(current) : visibleIncoming(current);
                edges.forEach(function (edge) {
                  foundEdges.merge(edge);
                  const next = direction === 'out' ? edge.target() : edge.source();
                  if (!visited.has(next.id())) {
                    visited.add(next.id());
                    foundNodes.merge(next);
                    queue.push(next);
                  }
                });
              }
              return { nodes: foundNodes, edges: foundEdges };
            }

            function highlightSelection(fitSelection) {
              cy.elements().removeClass('selected-node dependency dependent transitive-dependency transitive-dependent highlight-edge dimmed focused-out');
              if (!selectedModule) return;
              const selected = cy.getElementById(selectedModule);
              if (!selected.length) return;
              selected.removeClass('hidden-by-filter').addClass('selected-node');
              const directDependencies = visibleOutgoing(selected).targets();
              const directDependents = visibleIncoming(selected).sources();
              directDependencies.addClass('dependency');
              directDependents.addClass('dependent');
              let neighborhood = selected.union(directDependencies).union(directDependents);
              let highlightedEdges = visibleOutgoing(selected).union(visibleIncoming(selected));
              if (transitive.checked) {
                const dependencies = traverse(selected, 'out');
                const dependents = traverse(selected, 'in');
                dependencies.nodes.difference(directDependencies).addClass('transitive-dependency');
                dependents.nodes.difference(directDependents).addClass('transitive-dependent');
                neighborhood = neighborhood.union(dependencies.nodes).union(dependents.nodes);
                highlightedEdges = highlightedEdges.union(dependencies.edges).union(dependents.edges);
              }
              highlightedEdges.addClass('highlight-edge');
              if (focus.checked) {
                cy.elements().addClass('focused-out');
                neighborhood.union(highlightedEdges).removeClass('focused-out');
              }
              renderModuleDetails(selected);
              if (fitSelection) cy.animate({ fit: { eles: neighborhood.union(highlightedEdges), padding: 70 }, duration: 250 });
            }

            function selectModule(path, fitSelection) {
              const exact = model.nodes.find(function (node) { return node.path === path; });
              const partial = model.nodes.find(function (node) { return node.path.toLowerCase().includes(path.toLowerCase()); });
              const match = exact || partial;
              if (!match) return;
              selectedModule = match.path;
              search.value = match.path;
              highlightSelection(fitSelection);
              const params = new URLSearchParams();
              params.set('module', match.path);
              if (focus.checked) params.set('focus', '1');
              history.replaceState(null, '', '#' + params.toString());
            }

            function pills(values) {
              return values.map(function (value) { return '<span class="pill">' + escapeHtml(value) + '</span>'; }).join('');
            }

            function escapeHtml(value) {
              const element = document.createElement('div');
              element.textContent = value;
              return element.innerHTML;
            }

            function renderModuleDetails(node) {
              const outgoing = visibleOutgoing(node);
              const incoming = visibleIncoming(node);
              function edgeRows(edges, direction) {
                if (!edges.length) return '<li class="muted">None in the selected scopes.</li>';
                return edges.map(function (edge) {
                  const other = direction === 'out' ? edge.target().id() : edge.source().id();
                  return '<li><button data-module="' + escapeHtml(other) + '" class="module-path">' + escapeHtml(other) +
                    '</button><br>' + pills(edge.data('scopeList')) + '<br><span class="muted">' +
                    escapeHtml(edge.data('configurations')) + '</span></li>';
                }).join('');
              }
              details.innerHTML = '<p class="module-path"><strong>' + escapeHtml(node.id()) + '</strong></p>' +
                '<p>Kind: ' + escapeHtml(node.data('kind')) + '<br>Directory: ' + escapeHtml(node.data('directory')) + '</p>' +
                '<h2>Dependencies (' + outgoing.length + ')</h2><ul class="edge-list">' + edgeRows(outgoing, 'out') + '</ul>' +
                '<h2>Dependents (' + incoming.length + ')</h2><ul class="edge-list">' + edgeRows(incoming, 'in') + '</ul>';
              details.querySelectorAll('[data-module]').forEach(function (button) {
                button.addEventListener('click', function () { selectModule(button.dataset.module, true); });
              });
            }

            function clearSelection() {
              selectedModule = null;
              search.value = '';
              details.textContent = 'Select a module or edge to inspect it.';
              cy.elements().removeClass('selected-node dependency dependent transitive-dependency transitive-dependent highlight-edge dimmed focused-out');
              history.replaceState(null, '', location.pathname + location.search);
              applyFilters();
            }

            cy.on('tap', 'node', function (event) { selectModule(event.target.id(), false); });
            cy.on('tap', 'edge', function (event) {
              const edge = event.target;
              details.innerHTML = '<p><strong>' + escapeHtml(edge.source().id()) + ' → ' + escapeHtml(edge.target().id()) +
                '</strong></p><h2>Scopes</h2>' + pills(edge.data('scopeList')) +
                '<h2>Declaring configurations</h2><p class="module-path">' + escapeHtml(edge.data('configurations')) + '</p>';
            });
            document.getElementById('select-module').addEventListener('click', function () { selectModule(search.value, true); });
            search.addEventListener('keydown', function (event) { if (event.key === 'Enter') selectModule(search.value, true); });
            document.getElementById('fit-graph').addEventListener('click', function () { cy.fit(undefined, 35); });
            document.getElementById('reset-graph').addEventListener('click', function () {
              cy.layout({ name: 'breadthfirst', directed: true, padding: 36, spacingFactor: 1.25, animate: true }).run();
            });
            document.getElementById('clear-selection').addEventListener('click', clearSelection);
            transitive.addEventListener('change', function () { highlightSelection(true); });
            focus.addEventListener('change', function () { highlightSelection(true); });

            applyFilters();
            const hash = new URLSearchParams(location.hash.slice(1));
            if (hash.get('focus') === '1') focus.checked = true;
            if (hash.get('module')) selectModule(hash.get('module'), true);
            else cy.fit(undefined, 35);
          }());
        </script>
      </body>
      </html>
    """.trimIndent() + "\n"
  }

  private fun loadResource(path: String): String =
    requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
        "Cytoscape.js resource not found on the build-logic classpath: $path"
      }
      .bufferedReader(StandardCharsets.UTF_8)
      .use { it.readText() }

  private data class ModuleNode(
    val path: String,
    val name: String,
    val relativeDirectory: String,
    val kind: String,
  )

  private data class EdgeDeclaration(
    val source: String,
    val target: String,
    val configuration: String,
    val scope: String,
  )

  private data class ModuleEdge(
    val source: String,
    val target: String,
    val configurations: List<String>,
    val scopes: List<String>,
  )

  companion object {
    const val RECORD_SEPARATOR = "\u001F"
    private const val CYTOSCAPE_RESOURCE =
      "META-INF/resources/webjars/cytoscape/3.34.1/dist/cytoscape.min.js"
    private const val CYTOSCAPE_LICENSE_RESOURCE =
      "META-INF/resources/webjars/cytoscape/3.34.1/LICENSE"
  }
}
