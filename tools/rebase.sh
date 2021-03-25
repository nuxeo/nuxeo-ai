#!/bin/bash -xe
#
# Helper script to quickly sync master-2021 with master (ie: rebase while they don't diverge)
#
# (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Contributors:
#      jcarsique
#

hub sync || echo "WARN: 'hub' command failed but master ref-masterFor2021 master-2021 branches must be up to date with origin."
# checkout local branches for convenience
git checkout master ; git checkout ref-masterFor2021 ; git checkout master-2021
rm -f .git/AI_REBASE
git show -s --pretty=format:'%C(auto)%h%d' master ref-masterFor2021 master-2021 | tee .git/AI_REBASE
if [[ -z $(git rev-list ref-masterFor2021..master) ]]; then
    echo "No changes to backport from master to master-2021"
    exit 0
fi
git checkout master-2021
git rebase -Xours --onto master ref-masterFor2021 master-2021
version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -N)
echo $version
if [[ ! $version =~ "2021-SNAPSHOT" ]]; then
    version=${version/SNAPSHOT/2021-SNAPSHOT}
fi
mvn -Pinternal versions:set versions:update-child-modules versions:set-property -Dproperty=nuxeo.ai.version -DnewVersion="$version" -DprocessDependencies=false -DgenerateBackupPoms=false
git commit -m"rebase: fix version $version" -a --no-edit ||true
git update-ref refs/heads/ref-masterFor2021 master
if [ -z "$(git log master-2021..master)" ] ; then
    git push -f origin master-2021 ref-masterFor2021
else
    echo "Please review master-2021: there should be no commit in the range master-2021..master (backup info in .git/AI_REBASE)"
    echo "Then issue: git push -f origin master-2021 ref-masterFor2021"
fi
