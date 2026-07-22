#!/usr/bin/env bash
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly backmerge_script="$script_dir/backmerge-main-to-dev.sh"
readonly fixture_root="$(mktemp -d)"
readonly remote="$fixture_root/remote.git"
readonly seed="$fixture_root/seed"
readonly worker="$fixture_root/worker"
readonly dirty_worker="$fixture_root/dirty-worker"
readonly -a generated_files=(
  "CHANGELOG.md"
  "README.md"
  "gradle.properties"
  "patches-bundle.json"
  "patches-list.json"
)

cleanup() {
  case "$fixture_root" in
    "${TMPDIR:-/tmp}"/tmp.*) rm -rf -- "$fixture_root" ;;
    *) echo "Refusing to remove unexpected fixture path: $fixture_root" >&2 ;;
  esac
}
trap cleanup EXIT

git init --bare --quiet "$remote"
git init --quiet "$seed"
git -C "$seed" config user.name "Back-merge test"
git -C "$seed" config user.email "backmerge-test@example.invalid"

for path in "${generated_files[@]}"; do
  printf 'dev metadata\n' > "$seed/$path"
done
printf 'shared\n' > "$seed/source.txt"
git -C "$seed" add .
git -C "$seed" commit --quiet -m "initial dev"
git -C "$seed" branch -M dev
git -C "$seed" remote add origin "$remote"
git -C "$seed" push --quiet --set-upstream origin dev

git -C "$seed" switch --quiet -c main
printf 'main source change\n' >> "$seed/source.txt"
for path in "${generated_files[@]}"; do
  printf 'main metadata\n' > "$seed/$path"
done
git -C "$seed" add .
git -C "$seed" commit --quiet -m "main release"
git -C "$seed" push --quiet --set-upstream origin main

git clone --quiet --branch main "$remote" "$worker"
printf 'post-release regeneration\n' > "$worker/patches-list.json"
(
  cd "$worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
)

git -C "$worker" fetch --quiet origin main dev
git -C "$worker" merge-base --is-ancestor origin/main origin/dev
test "$(git -C "$worker" show origin/dev:patches-list.json)" = "dev metadata"
test "$(git -C "$worker" show origin/dev:source.txt | tail -n 1)" = "main source change"

git clone --quiet --branch main "$remote" "$dirty_worker"
printf 'unexpected source edit\n' >> "$dirty_worker/source.txt"
if (
  cd "$dirty_worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
); then
  echo "Back-merge unexpectedly discarded a source change." >&2
  exit 1
fi
grep -q "unexpected source edit" "$dirty_worker/source.txt"
