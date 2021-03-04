#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail

VERSION=$1

jx step create pr regex --regex '^(?m)\s+repo: nuxeo-ai\n.*\s*version: (.*)$' --version "${VERSION}" \
  --files dependency-matrix/matrix.yaml \
  --repo https://github.com/nuxeo/nuxeo-ai-integration.git --base master --branch master --dry-run="$DRY_RUN"
jx step create pr regex --regex '(?m)ai-core-parent.*$\n^\s+<version>(.*)<\/version>$' --version "${VERSION}" \
  --files pom.xml \
  --repo https://github.com/nuxeo/nuxeo-ai-integration.git --base master --branch master --dry-run="$DRY_RUN"
