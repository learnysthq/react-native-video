#!/bin/bash
UPSTREAM_BRANCH=${1:-upstream/master}
AUTHOR1=${2:-"keshav raj"}
AUTHOR2=${3:-"Learnyst"}
AUTHOR3=${4:-"sridhar"}

echo "🔍 Fetching upstream..."
git fetch upstream >/dev/null 2>&1

BASE=$(git merge-base HEAD $UPSTREAM_BRANCH)
echo "🧩 Common ancestor: $BASE"

echo "📜 Commits by $AUTHOR1 and $AUTHOR2 since fork:"
git log $BASE..HEAD --pretty="%h | %an | %s" --author="$AUTHOR1" --author="$AUTHOR2"  --author="$AUTHOR3" 

echo "📦 Generating diff file (our_changes.patch)..."
git diff $BASE..HEAD --author="$AUTHOR1" --author="$AUTHOR2" --author="$AUTHOR3" > our_changes.patch

echo "✅ Done. See:"
echo "   our_changes.patch → full code diff"
echo "   git diff --stat $BASE..HEAD --author=\"$AUTHOR1\" --author=\"$AUTHOR2\" --author=\"$AUTHOR3\" → summary"