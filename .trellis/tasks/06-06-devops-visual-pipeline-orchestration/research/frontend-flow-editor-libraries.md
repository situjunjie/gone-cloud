# Frontend flow editor libraries research

## Repo constraints

* The backend repo does not include the active admin frontend source.
* README says admin frontend options are Vue3 + element-plus and Vue3 + vben/ant-design-vue.
* The DevOps visual pipeline editor should therefore favor Vue3-compatible graph editors and avoid React-only libraries unless there is a strong reason.

## Candidates

### Vue Flow

Official docs: https://vueflow.dev/guide/ and https://vue-flow-docs.netlify.app/examples/dnd

Useful facts:

* Vue Flow targets interactive flowcharts and graphs.
* It supports custom nodes/edges, background, minimap, controls, node toolbar, node resizer.
* It includes built-in element dragging, zooming, panning, selection, reactive state, graph helpers, and composables.
* The official drag-and-drop example demonstrates adding nodes from a sidebar to an existing graph.
* It is written in TypeScript and released under MIT.

Fit:

* Best MVP fit for Vue3 management UI.
* Good when the product owns the backend DSL and only needs the frontend to edit nodes/edges.
* Lower ceremony than BPMN or a full graph engine.

Risks:

* Less suitable than X6 if we need heavy graph editing features, advanced auto-layout, sophisticated routing, or very large graphs.

### AntV X6

Official docs: https://x6.antv.antgroup.com/en/tutorial/about

Useful facts:

* X6 is a graph editing engine based on HTML and SVG.
* It supports DAG diagrams, ER diagrams, flowcharts, lineage graphs.
* It supports custom node styles/interactions using SVG, HTML, React, Vue, and Angular.
* It has built-in graph editing extensions such as selection, alignment lines, minimap, plus plugins for DnD, stencil, history, keyboard, clipboard, scroller, export.
* It is data-driven and event-driven.

Fit:

* Strong candidate for a mature, highly editable DAG canvas.
* Better than Vue Flow if the editor must feel like a professional diagramming tool with stencil, snaplines, clipboard, history, export, and complex interactions.

Risks:

* Higher implementation complexity.
* More graph-engine concepts for the team to learn.

### LogicFlow

Official docs/GitHub: https://github.com/didi/LogicFlow and https://site.logic-flow.cn/en/article/architecture-of-logicflow/

Useful facts:

* LogicFlow is a flowchart editing framework focused on business customization.
* It provides flowchart interaction/editing, flexible node customization, and plugin mechanisms.
* It supports scenarios such as mind maps, ER diagrams, UML, workflows, IVR, work order flow, and intelligent robot flows.
* It supports data conversion between LogicFlow data and BPMN/Turbo-like backend execution structures.
* License is Apache-2.0.

Fit:

* Good if the team wants a Chinese ecosystem and business-flow-oriented editor.
* Useful if later we need BPMN/Turbo conversion.

Risks:

* For DevOps pipeline DSL, its business-flow positioning may be less direct than Vue Flow or X6.

### bpmn-js

Official docs: https://bpmn.io/toolkit/bpmn-js/download/ and https://bpmn.io/toolkit/bpmn-js/walkthrough/

Useful facts:

* bpmn-js is a BPMN 2.0 rendering toolkit and web modeler.
* It can be used as viewer or modeler.
* It reads/writes BPMN XML through BPMN moddle and supports BPMN modeling rules.
* Current official download page lists bpmn-js v18.16.0 and npm install support.

Fit:

* Best if the DevOps pipeline itself must be a BPMN 2.0 process or directly interoperate with BPMN/Flowable.
* Strong for standards-based process modeling.

Risks:

* Too heavy and semantically mismatched if the pipeline is a technical CI/CD DAG.
* Frontend must customize BPMN palette/properties heavily to make it feel like a DevOps pipeline editor.

### Rete.js

Official docs: https://retejs.org/docs/

Useful facts:

* Rete.js v2 is a framework for visual interfaces and workflows.
* It supports visualization using React, Vue, Angular, Svelte, or Lit.
* It offers processing engines for dataflow and control flow.
* Its ecosystem is modular: core package plus plugins for area, connection, engine, minimap, history, readonly, Vue renderer, and more.

Fit:

* Good for visual programming, dataflow, and workflows where node execution semantics are central.
* Useful if we want browser-side graph execution or complex node I/O sockets.

Risks:

* Higher conceptual load than needed for a CI/CD pipeline editor.
* Node socket/dataflow semantics may not match a simple stage pipeline.

## Recommendation

Use Vue Flow for MVP.

Why:

* Best alignment with Vue3 admin UI.
* Enough primitives for node palette, drag-and-drop, edges, custom node rendering, minimap, controls, and properties panel.
* Keeps execution semantics in backend DSL instead of coupling to a diagram standard.
* Faster to deliver a usable editor for the first important DevOps pipeline feature.

Upgrade path:

* If the editor becomes graph-tool-heavy, migrate to X6 before too much custom canvas behavior accumulates.
* If product decides to model DevOps pipeline as BPMN and run it through Flowable/Camunda-like semantics, use bpmn-js instead.
* If future pipeline nodes become true visual programming/dataflow nodes with typed sockets and local graph execution, revisit Rete.js.
