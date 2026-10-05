# Linked subtasks — Todoist v12190

The patch styles task rows after Todoist finishes both full and payload binds. It reads
actual task parent IDs, follows ancestors present in the adapter, and applies 28dp per
level (maximum six), a gray vertical guide/red arrow, and 95% title/description text.
A child whose parent is filtered out still receives one level. The patch changes
list presentation only, without writing task priorities, parent IDs, or ordering back
to Todoist. It does not target board/calendar/widget renderers.

## Hierarchical order

Before the task-list adapter consumes a new list, preserve the original relative order
of its visible roots and emit each root followed by its descendants in preorder.
Only direct siblings are compared by priority: P1, P2, P3, P4. Equal-priority siblings
keep their original relative order. A descendant's priority cannot move its parent.
For example, an existing P1 root stays ahead of a P2 root; that P2 root is immediately
followed by its own P1 child and then its P3 child. Each child's descendants follow
that child before the next sibling.

Children follow their parent across display groups. Section headers and non-task rows
remain in their original relative order; a group emptied by moving its children can
retain its header. A child whose parent is absent remains a visible root at its normal
position. Only tasks already present in the submitted list are considered: the patch
does not fetch hidden/filtered/collapsed children. Cycles terminate without losing
rows; ambiguous duplicate IDs preserve the input unchanged. Traversal is iterative,
so the six-level visual indentation cap never truncates the actual task list.

The transformation happens before both the backing-list assignment and notification,
not during binding. Other adapter types bypass it. Existing drag/freeze handling stays
in place; the hierarchy is reapplied on subsequent list submissions.

The previous implementation skipped styling when `itemView.getParent()` was null.
RecyclerView normally binds new holders before attaching them, so this skipped initial
rows. It also started the depth counter at zero *after* obtaining the direct parent,
leaving direct children unstyled. A count-only ancestor cache became stale on edits
that kept the list size unchanged, and Android rejects small keyed `setTag` IDs such
as `0x7101` when those style paths eventually execute.

The corrected patch uses no obfuscated class/member names or fixed caller registers.
It identifies the binder by payload strings, the holder by its view fields, the
indentation delegate by its signature and padding API, and ID/parent accessors by the
TreeCache grouping constructor. The original binder is preserved as a private method,
called by a wrapper before a separate style helper. All other references are derived
from the APK. Ambiguous matches fail rather than choosing an arbitrary method.

Ancestor data is rebuilt for each subtask bind, so same-size list replacement and
reparenting do not retain a stale cache. This costs a linear scan of the displayed
adapter items per subtask bind. The style does not require an attached RecyclerView.
Padding, text size, and the previous foreground are restored when a holder is reused
for a top-level task. Keyed view tags use an application-range high byte. Guide insets
use START gravity for RTL layouts, and the triangular arrow head is mirrored in RTL.
The priority getter is discovered from the holder's checkmark renderer, without using
its obfuscated name. Priority buckets use Todoist's stored values (P1=4 through P4=1).

## Automated regression

Supply your own unpatched APK; no APK is included in the repository:

```sh
./gradlew -I tests/todoist/linked-subtasks.init.gradle :patches:testLinkedSubtasks \
  -PtodoistApk=/absolute/path/to/todoist.apk
./gradlew :patches:buildAndroid
```

The test sources and opt-in init script live under `tests/todoist`. They do not alter
`patches/build.gradle.kts`, participate in normal builds, or enter the patch bundle.
Keep them in the PR to cover the nontrivial hierarchy transformation. Exclude generated
`patches/bin`, build outputs, and APKs from commits.

The regression applies the actual patch to the APK's DEX, serializes/reloads the
modified adapter, then repeats with renamed R8 classes, fields, and methods. A small
DEX interpreter executes the injected depth and ordering instructions for roots,
direct children, grandchildren, priority conflicts, equal-priority stability,
idempotence, missing parents, same-size reparenting, cross-section children, duplicate
IDs, other-adapter bypass, 1,000-level chains, and cycles. It also verifies that an
arrow-head shape is installed.
It does not emulate Android's rendering or ART's verifier.

## Device validation

Patch a fresh v12190 APK with the resulting `.mpp` and select **Linked subtasks**.
Check a project list and Today/filter view with a root, child, and grandchild. Include
a P2 parent with P1/P3 children and another P1 root to verify independent root ordering;
include
a filter where the parent is absent. Scroll out and back to recycle holders, change
a child's parent without adding/removing tasks, and expand/collapse or drag rows.
Confirm roots recover their original padding/font/foreground, repeated refreshes do
not keep shrinking text, and guides remain aligned in light/dark and RTL modes.
