#!groovy

def ocMaster =  env.OC_MASTER
def remoteDocker = env.DOCKER_REPO 
def clusterBaseName = env.CLUSTER_DNS_BASE

def ocNamespace = "demo-cd"

def isMaster = 'master'.equalsIgnoreCase(env.BRANCH_NAME)
def featureId = featureFromBranch(env.BRANCH_NAME)
def deployId = featureId.toLowerCase()
def projectId = 'demo-be'

def instanceId = "${projectId}-${deployId}"

def imageId = "${projectId}:${featureId}"
def localImage = "local/${imageId}"
def remoteImage = "${remoteDocker}/${imageId}"

def deployThisYaml = "target/${projectId}-deploy.yaml"
def deployTemplateFile = "deploy/oc-full-deploy.yaml"

def now = new Date().format("dd-MM-yyyy HH:mm:ss.SSS", TimeZone.getTimeZone('CET')) + " CET"

def fullDeploy = true

env.OC_NS = ocNamespace
env.OC_SERVER = ocMaster

// Reference the GitLab connection name from your Jenkins Global configuration (http://JENKINS_URL/configure, GitLab section)
properties([[$class: 'GitLabConnectionProperty', gitLabConnection: 'Gitlab Connection']])

node {
timestamps {
  // redefine, cannot be done in the def section
  fullDeploy = (!isMaster || !isDeployed(instanceId))

  echo "Branch is: ${env.BRANCH_NAME}, featureId is: ${featureId},  isMaster: ${isMaster}, fullDeploy: ${fullDeploy}"

  stage ('Prepare, build and package') {
    env.PATH = "${tool 'Maven3'}/bin:${env.PATH}"

    checkout scm

    updateGitlabCommitStatus name: 'build', state: 'pending'

    gitlabCommitStatus("build") {
      sh './gradlew clean'
      dir('docker') {
          sh 'rm -f *.jar'
      }

      def preLabels = readFile file: "src/main/resources/labels.properties"
      def labels = preLabels + "\n" +
        "APP=${projectId}\n"+
        "DEPLOYID=${deployId}\n"+
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

      sh './gradlew test build --info && cp build/libs/be*.jar docker'

      stash name: "deployDir", includes: "deploy/*"
      stash name: "dockerDir", includes: "docker/*"
    } // glcs
  } // stage


  stage('prepare deploy') {
    gitlabCommitStatus("prepare_deploy") {
      echo "Making a the deployment descriptor for ${featureId} ${deployId}"

      unstash name: "deployDir"

      def sedCmd = "-e \"s/@VersionTagGoesHere@/${featureId}/\" "
      sedCmd = sedCmd + " -e \"s/@LowcaseFeatureNameGoesHere@/${deployId}/\" "
      sedCmd = sedCmd + " -e \"s/@ProjectIdGoesHere@/${projectId}/\" "
      sedCmd = sedCmd + " -e \"s/@DockerImageRepoGoesHere@/${remoteDocker}/\" "
      sedCmd = sedCmd + " -e \"s/@ClusterBaseNameGoesHere@/${clusterBaseName}/\" "
      
      sh "sed ${sedCmd} ${deployTemplateFile}  >${deployThisYaml}"

      sh "cat ${deployThisYaml}"

      stash name: "deployConfig", includes: "target/*.yaml"
    }
  } // stage


  stage ('Clean Openshift') {
    if (fullDeploy) {
        // Always clean feature branches and clean master if no master DeploymentConfig is found
        echo "Cleaning openshift for deployment ${instanceId}"
        destroyInstance(instanceId)
    } // if
    echo "done cleaning openshift for deployment ${instanceId}"
  } // stage


  stage('docker build') {
    gitlabCommitStatus("docker_build") {
      unstash name: "dockerDir"
      dir ('docker') {
        echo "START: docker build ${localImage} and push to ${remoteImage}"
        sh "docker login -u ${env.NEXUS_PUSH_UID} -p ${env.NEXUS_PUSH_PWD} -e no.no.no ${remoteDocker}"
        sh "docker build -t ${localImage} ."
        sh "docker tag  ${localImage} ${remoteImage}"
      }
    }
  } // stage


  stage('docker push') {
    gitlabCommitStatus("docker_push") {
      // separate stage as it takes some time
      sh "docker push ${remoteImage}"
    }
  }


  stage('deploy') {  
    gitlabCommitStatus("oc_deploy") {
      def verbose = 'true'

      if (!fullDeploy) {
        echo "Master is already deployed, only kick its current deployment"
        deployLatest(instanceId)
        callOpenshift("label -f ${deployThisYaml} belongsTo=${instanceId} --overwrite=true")
      }
      else {
        echo "Full deployment of all components of ${instanceId}"
        // full deploy when it's not the master
        // Get the OC deployment yaml config	
        unstash name: "deployConfig"
        def createyaml = readFile file: deployThisYaml

    	try {
            // create OC resources
            openshiftCreateResource jsonyaml: createyaml, namespace: ocNamespace, authToken: "${env.OC_AUTH_TOKEN}", verbose: verbose
            }
            finally {
              // in a finally to label as much items in case of an error
              callOpenshift("label -f ${deployThisYaml} belongsTo=${instanceId} --overwrite=true")
            }
          }
    }
  } // stage


  stage('inform') {
    gitlabCommitStatus("inform") {
      addGitLabMRComment('CI: Deployed and tested OK')
    }
  }

  stage('destroy') {
    if (!isMaster) {
        input 'Ready to destroy after use?'
        destroyInstance("${instanceId}")
    } // if
    echo "done cleaning openshift for deployment ${instanceId}"
  } // stage

 
} // timestamps
} // node


def deployLatest(instanceId) {
    callOpenshift("deploy --latest ${instanceId}")
}

def isDeployed(instanceId) {
    def dcFound = callOpenshift("get dc -o name")
    echo "dcFound.indexOf('${instanceId}')=" + dcFound.indexOf(instanceId)
    return dcFound.indexOf(instanceId) >= 0
}


def destroyInstance(instanceId) {
  gitlabCommitStatus("inform") {
    try {
       echo "Start scaling pods to zero on dc ${instanceId}"
       callOpenshift("scale dc ${instanceId} --replicas=0 --timeout=1m")
    } catch (err) {
       echo "Error absorbed scaling pods to zero: ${err}"
    }
    // delete with our belongsTo label
    callOpenshift("delete is,dc,svc,route --selector='belongsTo=$instanceId'")
    // rep-ctr needs another label selector
    callOpenshift("delete rc --selector='deploymentconfig=$instanceId'")
    echo "done cleaning openshift for deployment ${instanceId}"
  }
}


def featureFromBranch(branchname) {
  if ('master'.equalsIgnoreCase(branchname)) {
    return 'master'
  } 
  def matcher = branchname =~ '([0-9]+)-*'
  return matcher ? 'BR'+matcher[0][1] : 'UNK'
}


def callOpenshift(cmd) {
    def resStr = sh (
        script: "/usr/bin/oc $cmd --namespace=${env.OC_NS} --server=${env.OC_SERVER} --token=${env.OC_AUTH_TOKEN} --insecure-skip-tls-verify=true",
        returnStdout: true
    )
    echo "returned from $cmd: $resStr"
    return resStr
}

