#!/bin/bash -xe
#
# Helper script to update the versions a release tag and source branch
#
# (C) Copyright 2020 Nuxeo (http://nuxeo.com/).
# This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
# Notice of copyright on this source code does not indicate publication.
#
# Contributors:
#      jcarsique
#
# The script will:
# - create and push a release tag, ready for build
# - create a GitHub release
# - update the current branch with the next version
#
# Environment Variables:
# - VERSION: the version to release. If unset, it is retrieved from the Maven POM.
# - INCREMENT: the increment level policy for the next version (default is 'patch').
#   Level can be one of: major, minor, patch, release. Default level is 'patch'.
# - DRY_RUN: dry run if equals 'true'
#
# 3rd parties:
# - Git, Maven, Jenkins X, GNU sed
# - https://github.com/jenkins-x/jx-release-version
# - https://github.com/fsaintjacques/semver-tool/

jx step git credentials
git config credential.helper store

RELEASE_VERSION=${VERSION:-$(jx-release-version -same-release)}
INCREMENT=${INCREMENT:-patch}
NEXT_VERSION=$(semver bump "$INCREMENT" "$RELEASE_VERSION")

printf "Releasing %s\n\tVersion:\t%s\n\tNext version:\t%s\n" "$(git remote get-url origin)" "$RELEASE_VERSION" "$NEXT_VERSION"
rm -f release.properties
{
  echo "RELEASE_VERSION=$RELEASE_VERSION"
  echo "INCREMENT=$INCREMENT"
  echo "NEXT_VERSION=$NEXT_VERSION"
} >>release.properties
mvn -V -B versions:set versions:set-property -DnewVersion="$RELEASE_VERSION" -Dproperty=nuxeo.ai.version -DgenerateBackupPoms=false
sed -i "s,version: .*,version: $RELEASE_VERSION," charts/*/Chart.yaml
git add -u
if [ "$DRY_RUN" = 'true' ]; then
  echo "Dry run: skip 'jx step next-version' and 'jx step changelog'"
  git diff --cached
else
  jx step next-version --version="$RELEASE_VERSION" -t
  jx step changelog -v "v$RELEASE_VERSION"
fi

if [ "$INCREMENT" != 'release' ]; then
  # Not including the release tag in master history
  git reset --hard "origin/$BRANCH"

  mvn -B versions:set versions:set-property -DnewVersion="${NEXT_VERSION}-SNAPSHOT" -Dproperty=nuxeo.ai.version -DgenerateBackupPoms=false
  sed -i "s,version: .*,version: ${NEXT_VERSION}-SNAPSHOT," charts/*/Chart.yaml
  git add -u
  jx step next-version --version="$NEXT_VERSION"
  git commit -a -m"Post release ${RELEASE_VERSION} set version ${NEXT_VERSION}-SNAPSHOT"
  if [ "$DRY_RUN" = 'true' ]; then
    echo "Dry run: skip 'git push' and 'jx start pipeline'"
    git show
  else
    git push origin "$BRANCH"
  fi
fi
chmod +r VERSION charts/*/templates/release.yaml || true
