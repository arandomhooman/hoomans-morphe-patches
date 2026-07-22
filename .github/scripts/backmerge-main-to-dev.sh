#!/usr/bin/env bash
set -euo pipefail

readonly remote="${BACKMERGE_REMOTE:-origin}"
readonly source_branch="${BACKMERGE_SOURCE_BRANCH:-main}"
readonly target_branch="${BACKMERGE_TARGET_BRANCH:-dev}"
readonly commit_message="${BACKMERGE_COMMIT_MESSAGE:-chore: back-merge main into dev [skip ci]}"
readonly -a generated_files=(
  "CHANGELOG.md"
  "README.md"
  "gradle.properties"
  "patches-bundle.json"
  "patches-list.json"
)

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git fetch "$remote" "$source_branch" "$target_branch"

# The publish phase can regenerate release metadata after semantic-release commits it. Clear only
# those known generated files before switching branches, and refuse to discard source changes.
git restore --source=HEAD --staged --worktree -- "${generated_files[@]}"
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Back-merge worktree has unexpected tracked changes:" >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

git switch --force-create "$target_branch" "$remote/$target_branch"

merge_status=0
git merge --no-commit --no-ff "$remote/$source_branch" || merge_status=$?

if ! git rev-parse --verify --quiet MERGE_HEAD >/dev/null; then
  if (( merge_status != 0 )); then
    echo "Back-merge failed before a merge could start." >&2
    exit "$merge_status"
  fi

  echo "$target_branch already contains $source_branch."
  exit 0
fi

mapfile -t conflicts < <(git diff --name-only --diff-filter=U)
declare -A generated_file_set=()
for path in "${generated_files[@]}"; do
  generated_file_set["$path"]=1
done

unexpected_conflicts=()
for path in "${conflicts[@]}"; do
  if [[ -z "${generated_file_set[$path]+set}" ]]; then
    unexpected_conflicts+=("$path")
  fi
done

if (( ${#unexpected_conflicts[@]} > 0 )); then
  printf 'Back-merge has non-generated conflicts:\n' >&2
  printf '  %s\n' "${unexpected_conflicts[@]}" >&2
  git merge --abort
  exit 1
fi

# These files describe each branch's own release and must stay on the target branch.
git restore --source=HEAD --staged --worktree -- "${generated_files[@]}"

mapfile -t unresolved < <(git diff --name-only --diff-filter=U)
if (( ${#unresolved[@]} > 0 )); then
  printf 'Back-merge still has unresolved conflicts:\n' >&2
  printf '  %s\n' "${unresolved[@]}" >&2
  git merge --abort
  exit 1
fi

git commit -m "$commit_message"
git push "$remote" "$target_branch"
