#!/bin/bash
set -o errexit -o pipefail -o noclobber -o nounset

#
# (C) Copyright 2020-2021 Nuxeo (http://nuxeo.com/) and others.
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
#     jcarsique
#
# Development sprint iteration
#

GIT_TOP_LVL=$(git rev-parse --show-toplevel)
cd "$GIT_TOP_LVL"
CONFIG_FILE=$GIT_TOP_LVL/.git/SPRINT

#if [ $# -eq 0 ]; then
#    echo "No arguments provided"
#    exit 1
#fi

#case $1 in
#    -t|--target) target="$2"; shift ;;
#    -u|--uglify) uglify=1 ;;
#    *) echo "Unknown parameter passed: $1"; exit 1 ;;
#esac


hub sync || git fetch origin

if [[ -f $CONFIG_FILE ]]; then
    # shellcheck source=.git/SPRINT
    source "$CONFIG_FILE"
else
    PS3="Pick a number: "
    mapfile -t options < <(git branch -r --list "origin/sprint*" |cut -f 2- -d /)
    echo "Sprint to merge?"
    select oldSprint in "${options[@]}"; do
        if [[ -z $oldSprint ]]; then
            echo "invalid answer $REPLY, choose a number"
            continue
        fi
        break
    done
    echo
    mapfile -t options < <(git branch -r --list "origin/master*" |cut -f 2- -d /)
    echo "Base branch?"
    select base in "${options[@]}"; do
        if [[ -z $oldSprint ]]; then
            echo "invalid answer $REPLY, choose a number"
            continue
        fi
        break
    done
    echo
    read -rp "New sprint name? " newSprint

    cat > "$(git rev-parse --show-toplevel)/.git/SPRINT" <<EOF
oldSprint=$oldSprint
newSprint=$newSprint
base=$base
EOF
fi
echo
echo "Old Sprint:       $oldSprint"
echo "New Sprint:       $newSprint"
echo "Base branch:      $base"
read -p "Continue? " -n 1 -r
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    exit 1
fi
echo

function mergeOldSprint() {
    if [[ -n $(git log "$oldSprint".."origin/$base") ]]; then
        echo "Rebase required"
#        oldSprintId=$(git rev-parse --short origin/"$oldSprint")
        git checkout "$oldSprint"
        git rebase "origin/$base"
        git logone ...'@{u}'
        git fetch origin
        git id "origin/$oldSprint"
        git status -sb
        echo "Please review the rebase of $oldSprint onto $base, then force push and execute again sprint.sh"
#        echo "Please handle the following, then execute again sprint.sh:
#git push -f
#gh pr create -B "$base" -H "$oldSprint" -l "work in progress"
#gh pr checks
#git fetch origin
#git logone "origin/$oldSprint".. # must be empty: no commit on base that is missing on sprint
#gh pr merge -md
#"
        exit
    fi

    echo "MERGE $oldSprint -> $base"
    git checkout "$base"
    git merge --no-ff --no-edit "$oldSprint"
    git log --oneline '@{u}'..
    echo
    read -p "Push $base? " -n 1 -r
    if [[ ! $REPLY =~ ^[Yy]$ ]]
    then
        exit 1
    fi
    echo
    git push origin "$base"

    # TODO update nuxeo-ai-integration
}

function createNewSprint() {
    echo "CREATE $newSprint <- $base"
    git checkout -b "$newSprint" "origin/$base" --no-track
    currentVersion="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"
#    nextVersion="${currentVersion/-SNAPSHOT/-$newSprint-SNAPSHOT}"
    nextVersion="$(semver bump minor "$currentVersion")-SNAPSHOT"
    git grep --name-only "$currentVersion" |xargs sed -i "s,$currentVersion,$nextVersion,g"
    git commit -m"set version $nextVersion" -a
    git log --oneline "$base"..
    echo
    read -p "Push new branch $newSprint and delete old $oldSprint? " -n 1 -r
    if [[ ! $REPLY =~ ^[Yy]$ ]]
    then
        exit 1
    fi
    echo
    git push --set-upstream origin "$newSprint:$newSprint"
    git branch -d "$oldSprint"
    git push --delete origin "$oldSprint"
    rm "$CONFIG_FILE"
}

mergeOldSprint
createNewSprint
