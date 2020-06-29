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

RELEASE_VERSION=${VERSION:-$(jx-release-version)}
INCREMENT=${INCREMENT:-patch}
NEXT_VERSION=$(semver bump "$INCREMENT" "$RELEASE_VERSION")

printf "Releasing %s\n\tVersion:\t%s\n\tNext version:\t%s\n" $(git remote get-url origin) "$RELEASE_VERSION" "$NEXT_VERSION"

mvn -V -B versions:set -DnewVersion="$RELEASE_VERSION" -DgenerateBackupPoms=false
git add -u
if [ "$DRY_RUN" = 'true' ]; then
    echo "Dry run: skip 'jx step next-version' and 'jx step changelog'"
    git diff --cached
else
    jx step next-version --version="$RELEASE_VERSION" -t
    jx step changelog -v "v$RELEASE_VERSION"
fi

# Not including the release tag in master history
git reset --hard "origin/$BRANCH"

mvn -B versions:set -DnewVersion="${NEXT_VERSION}-SNAPSHOT" -DgenerateBackupPoms=false
jx step next-version --version="$NEXT_VERSION"
git commit -a -m"Post release ${RELEASE_VERSION}. Set version ${NEXT_VERSION}."
if [ "$DRY_RUN" = 'true' ]; then
    echo "Dry run: skip 'git push' and 'jx start pipeline'"
    git show
else
    git push origin "$BRANCH"
    jx start pipeline nuxeo/nuxeo-ai --branch "v$RELEASE_VERSION" || true
fi
