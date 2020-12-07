/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *  Contributors:
 *      jcarsique
 */

void setGitHubBuildStatus(String context) {
    step([
            $class       : 'GitHubCommitStatusSetter',
            reposSource  : [$class: 'ManuallyEnteredRepositorySource', url: 'https://github.com/nuxeo/nuxeo-ai'],
            contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: context],
            errorHandlers: [[$class: 'ShallowAnyErrorHandler']]
    ])
}

pipeline {
    agent {
        label "jenkins-ai-nuxeo11"
    }
    parameters {
        string(name: 'BRANCH', defaultValue: 'master', description: 'Branch to release from.')
        string(name: 'VERSION', description: '''The version to release.
Leave it unset to use the version of the Maven POM.
The version must match "X.Y.Z[-PRERELEASE][+BUILD]"''')
        choice(name: 'INCREMENT', choices: ['patch', 'minor', 'major', 'release'], description: '''Semantic versioning increment.
The "release" increment mode removes any PRERELEASE or BUILD parts (see VERSION).''')
        booleanParam(name: 'DRY_RUN', defaultValue: true, description: 'Dry run')
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '3'))
        timeout(time: 1, unit: 'HOURS')
    }
    stages {
        stage('Release') {
            steps {
                setGitHubBuildStatus('release')
                container('platform11') {
                    withEnv(["BRANCH=${params.BRANCH}", "VERSION=${params.VERSION}", "INCREMENT=${params.INCREMENT}",
                             "DRY_RUN=${params.DRY_RUN}"]) {
                        script {
                            sh './tools/release.sh'
                            sh 'chmod +r VERSION charts/*/templates/release.yaml || true'
                            if ("$DRY_RUN" != 'true') {
                                def releaseProps = readProperties file: 'release.properties'
                                def jobName = '/nuxeo/nuxeo-ai/v' + releaseProps['RELEASE_VERSION']
                                if (!Jenkins.instance.getItemByFullName(jobName)) {
                                    build job: '/nuxeo/nuxeo-ai/', propagate: false, wait: true
                                }
                                build job: jobName, propagate: false, wait: false
                            }
                        }
                    }
                }
            }
            post {
                always {
                    setGitHubBuildStatus('release')
                    archiveArtifacts artifacts: 'release.properties, charts/*/templates/release.yaml', allowEmptyArchive: true
                }
            }
        }
        stage('Update downstream repos') {
            steps {
                setGitHubBuildStatus('post-release')
                container('platform11') {
                    withEnv(["DRY_RUN=${params.DRY_RUN}"]) {
                        script {
                            def nextVersion = readProperties(file: 'release.properties')['NEXT_VERSION'].trim()
                            sh """#!/bin/bash -xe
./tools/updatebot.sh ${nextVersion}
"""
                        }
                    }
                }
            }
            post {
                always {
                    setGitHubBuildStatus('post-release')
                }
            }
        }
    }
}
