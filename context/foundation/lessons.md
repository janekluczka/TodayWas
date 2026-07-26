# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## ViewModels expose a sealed Intent + onIntent(), and a sealed UiEvent flow

- **Context**: ViewModels under ui/<feature>/
- **Problem**: ViewModels grow ad-hoc public methods per action and single-purpose Flow<Unit> events that don't scale to new event types, drifting from MVI.
- **Rule**: ViewModels expose state via a single `fun onIntent(intent: XxxIntent)` dispatching a sealed `XxxIntent` (no per-action public methods), and a generic sealed `XxxUiEvent` exposed as `val events: Flow<XxxUiEvent>` for one-shot effects (never a single-purpose `Flow<Unit>`). Each state/intent/event/enum type lives in its own file under `ui/<feature>/`.
- **Applies to**: plan, plan-review, implement, impl-review
