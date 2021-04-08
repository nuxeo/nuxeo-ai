#!/bin/bash
set -o errexit
set -o nounset
set -o pipefail

VERSION=$1

jx step create pr regex --regex '^(?m)\s+repo: nuxeo-ai\n.*\s*version: (.*)$' --version "${VERSION}" \
  --files dependency-matrix/matrix.yaml \
  --repo https://github.com/nuxeo/nuxeo-ai-integration.git --base master-2021 --branch master-2021 --dry-run="$DRY_RUN"
