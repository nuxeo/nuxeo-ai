/*
 *
 *  * (C) Copyright 2020 Nuxeo (http://nuxeo.com/).
 *  * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 *  * Notice of copyright on this source code does not indicate publication.
 *  *
 *  * Contributors:
 *  *      jcarsique
 *
 *
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
        label "jenkins-ai-nuxeo1010"
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
                container('platform1010') {
                    withEnv(["BRANCH=${params.BRANCH}", "VERSION=${params.VERSION}", "INCREMENT=${params.INCREMENT}",
                             "DRY_RUN=${params.DRY_RUN}"]) {
                        sh './tools/release.sh'
                        sh 'chmod +r VERSION charts/*/templates/release.yaml || true'
                    }
                }
            }
            post {
                always {
                    setGitHubBuildStatus('release')
                    archiveArtifacts artifacts: 'VERSION, charts/*/templates/release.yaml', allowEmptyArchive: true
                }
            }
        }
    }
}
