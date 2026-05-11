# Frontend Agent Instructions

## Required Reading

Before modifying frontend code, read and follow `AGENTS_front.md`.

## Design Document Index

When the task involves screen, page, UI, layout, interaction, permission display, state display, or visual review, find and read the relevant documents under `docs/screen-design`.

Read only the documents relevant to the current task.

### Always consider for screen/page/UI work

- `docs/screen-design/README.md`
- `docs/screen-design/anti-patterns.md`
- `docs/screen-design/screen-labels.md`
- `docs/screen-design/screen-state-matrix.md`
- `docs/screen-design/permission-matrix.md`

### Read when relevant

- New screen or page layout: `docs/screen-design/wireframes.md`
- Styling, density, dashboard layout, panel layout, badges, visual hierarchy: `docs/screen-design/dense-tokens.md`
- Field-use context, Polifon, mobile/field UX assumptions: `docs/screen-design/field-context.md`
- High-fidelity validation or design review: `docs/screen-design/measurement-gates.md`
- Prototype generation workflow: `docs/screen-design/prototype-agent-loop.md`
- Research source tracking or rationale review: `docs/screen-design/user-research-source.md`
- Full screen design workflow review: `docs/screen-design/screen-design-workflow-checklist.md`

If a referenced document is missing or the file path differs, search the nearby docs directory and use the closest matching document. Do not invent rules when the document cannot be found.

## Language Policy

- Respond in Korean.
- Use English for code identifiers, file names, class names, function names, and type names.
- Use Korean for comments, error messages, test display names, and final explanations.
- Do not translate or rename existing project-specific domain terms.

## Work Rules

- Follow the existing project structure and coding style.
- Do not make unrelated changes.
- Do not rename existing components, routes, domain terms, API names, event names, or file names.
- If a requirement is ambiguous, ask me in plan mode before implementing.
- Prefer existing project conventions.
- After completing work, list modified files and summarize the implementation in Korean.
