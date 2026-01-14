def ownerEmail = "de-team@dbc.dk"
def ownerSlack = "de-notifications"

pipeline {
    agent { label "devel12" }
    triggers {
        gitlab(triggerOnPush: true, triggerOnMergeRequest: true, branchFilterType: 'All')
    }
    options {
        buildDiscarder(logRotator(artifactDaysToKeepStr: "", artifactNumToKeepStr: "", daysToKeepStr: "30", numToKeepStr: "30"))
        timestamps()
        gitLabConnection('isworker')
    }
    stages {
        stage("build") {
            steps {
                withSonarQubeEnv(installationName: 'sonarqube.dbc.dk') {
                    script {
                        def status = sh returnStatus: true, script:  """
                            rm -rf \$WORKSPACE/.repo/dk/dbc
                            mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo --no-transfer-progress clean
                            mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo --no-transfer-progress verify
                        """

                        def sonarOptions = "-Dsonar.branch.name=$BRANCH_NAME"
                        if (env.BRANCH_NAME != 'main') {
                            sonarOptions += " -Dsonar.newCode.referenceBranch=main"
                        }

                        status += sh returnStatus: true, script: """
                            mvn -B -Dmaven.repo.local=$WORKSPACE/.repo --no-transfer-progress $sonarOptions sonar:sonar
                        """

                        junit testResults: '**/target/surefire-reports/TEST-*.xml'

                        if (status != 0) {
                            error("build failed")
                        }
                    }
                }
            }
        }
        stage("quality gate") {
            steps {
                // wait for analysis results
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        stage("upload") {
            steps {
                script {
                    if (env.BRANCH_NAME == 'main') {
                        sh """
                            mvn -B -Dmaven.repo.local=$WORKSPACE/.repo --no-transfer-progress jar:jar deploy:deploy
                        """
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                if (env.BRANCH_NAME == 'main') {
                    emailext(
                            recipientProviders: [developers(), culprits()],
                            to: "${ownerEmail}",
                            subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} failed",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>The main build failed. Log attached. </p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: true,
                    )
                    slackSend(channel: "${ownerSlack}",
                            color: 'warning',
                            message: "${env.JOB_NAME} #${env.BUILD_NUMBER} failed and needs attention: ${env.BUILD_URL}",
                            tokenCredentialId: 'slack-global-integration-token')

                } else {
                    // this is some other branch, only send to developer
                    emailext(
                            recipientProviders: [developers()],
                            subject: "[Jenkins] ${env.BUILD_TAG} failed and needs your attention",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>${env.BUILD_TAG} failed and needs your attention. </p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: false,
                    )
                }
            }
            updateGitlabCommitStatus name: 'build', state: 'failed'
        }
        success {
            updateGitlabCommitStatus name: 'build', state: 'success'
        }
    }
}
