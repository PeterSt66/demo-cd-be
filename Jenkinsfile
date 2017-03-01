#!groovy

def namespace = 'demo-cd'
def isMaster = 'master'.equalsIgnoreCase(env.BRANCH_NAME)
def featureId = featureFromBranch(env.BRANCH_NAME)
def projectId = 'demo-be'
def instanceId = "${projectId}-${featureId}"
def imageId = "${projectId}:${featureId}"
def now = new Date().format("dd-MM-yyyy HH:mm:ss.SSS", TimeZone.getTimeZone('CET')) + " CET"

def fullDeploy = true
// Reference the GitLab connection name from your Jenkins Global configuration (http://JENKINS_URL/configure, GitLab section)
properties([[$class: 'GitLabConnectionProperty', gitLabConnection: 'Gitlab Connection']])

def deployPipeline

timestamps {

   node {

      def pipelineFlavor = whichPipeline(env.JOB_NAME)

      echo "@@@ Pipeline start; Branch is: ${env.BRANCH_NAME}, featureId is: ${featureId},  isMaster: ${isMaster}, flavor: ${pipelineFlavor}"

      stage ('Prepare, build and package') {
        env.PATH = "${tool 'Maven3'}/bin:${env.PATH}"

        checkout scm

        deployPipeline = load "./deploy/${pipelineFlavor}Pipeline.groovy"
        deployPipeline.init()

        // redefine fullDeploy, cannot be done in the def section
        fullDeploy = (!isMaster || !deployPipeline.isDeployed(namespace, instanceId))
        echo "@@@ fullDeploy: ${fullDeploy}"

        updateGitlabCommitStatus name: 'build', state: 'pending'

        gitlabCommitStatus("build") {

          //sh './gradlew clean'
          dir('docker') {
              sh 'rm -f *.jar'
          }

          def preLabels = readFile file: "src/main/resources/labels.properties"
          def labels = preLabels + "\n" +
            "APP=${projectId}\n"+
            "DEPLOYID=${featureId}\n"+
            "INSTANCEID=${instanceId}\n"+
            "FEATURE=${env.BRANCH_NAME}\n"+
            "BUILD_ON=${env.NODE_NAME}\n"+
            "JENKINS_VERSION=${env.JENKINS_VERSION}\n"+
            "BUILD_NR=${env.BUILD_NUMBER}\n"+
            "BUILD_TAG=${env.BUILD_TAG}\n"+
            "JOB_NAME=${env.JOB_NAME}\n"+
            "JAVA_BUILD_VERSION=${env.JAVA_VERSION}\n"+
            "BUILD_TS=${now}\n"
          writeFile text: labels, file: "src/main/resources/labels.properties"

          sh 'cat src/main/resources/labels.properties'

          sh './gradlew build && cp build/libs/be*.jar docker'

          stash name: "deployDir", includes: "deploy/*"
          stash name: "dockerDir", includes: "docker/*"
        } // glcs
      } // stage

      def deployConfig = deployPipeline.prepareDeployment(namespace, projectId, featureId, instanceId, fullDeploy)

      deployPipeline.cleanOldDeployment(namespace, instanceId, fullDeploy)

      dockerBuild(imageId)

      deployPipeline.deploy(namespace, projectId, featureId, deployConfig, fullDeploy)
   } // node

   // needs pipeline-milestone-step plugin, aborts older executions - ms-1 set's it up
   milestone(1)

   //inform needs to be outside of a node (no use keeping a worker occupied, so informAndDestroy() needs to declare its own node{..} block
   deployPipeline.informAndDestroy(namespace, instanceId, isMaster);

   milestone(2) // reaching ms-2 aborts all older builds that still haven't passed this milestone, ie are stuck on input

} // timestampsq



def dockerBuild(imageId) {
  stage('docker build') {
    gitlabCommitStatus("docker_build") {
      unstash name: "dockerDir"
      dir ('docker') {
        sh "docker login -u ${env.NEXUS_PUSH_UID} -p ${env.NEXUS_PUSH_PWD} -e no@no.no ${env.DOCKER_REPO}"
        echo "START: docker build ${env.DOCKER_REPO}/${imageId}"
        sh "docker build -t ${env.DOCKER_REPO}/${imageId} ."
      }
    }
  } // stage

  stage('docker push') {
    gitlabCommitStatus("docker_push") {
      // separate stage as it takes some time
        sh "docker push ${env.DOCKER_REPO}/${imageId}"
    }
  }
}

def whichPipeline(jobName) {
   def matcher = jobName =~ '.*-k8s/.*'  //for k8s it needs to contain the string: -k8s/
   def pipelineName =  matcher ?"Kube" : "Openshift"
   //echo "====== Pipeline for job ${jobName} is ${pipelineName}"
   return pipelineName
}


def featureFromBranch(branchname) {
   if ('master'.equalsIgnoreCase(branchname)) {
      return 'master'
   }
   def matcher = branchname =~ '([0-9]+)-*'
   def featureId = matcher ? 'BR'+matcher[0][1] : 'UNK'
   return featureId.toLowerCase()
}
