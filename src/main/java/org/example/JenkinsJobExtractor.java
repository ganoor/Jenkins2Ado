package org.example;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JenkinsJobExtractor {
    public static void main(String[] args) {
        String scriptContent = "<?xml version='1.1' encoding='UTF-8'?><flow-definition plugin=\"workflow-job@1326.ve643e00e9220\">  <actions>    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin=\"pipeline-model-definition@2.2150.v4cfd8916915c\"/>    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction plugin=\"pipeline-model-definition@2.2150.v4cfd8916915c\">      <jobProperties>        <string>jenkins.model.BuildDiscarderProperty</string>      </jobProperties>      <triggers/>      <parameters>        <string>Image_Version</string>        <string>Image_Release_Type</string>        <string>Image_Branch_Name</string>        <string>Image_Type</string>        <string>service_pack_location</string>        <string>service_pack_version</string>      </parameters>      <options/>    </org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction>  </actions>  <description></description>  <keepDependencies>false</keepDependencies>  <properties>    <jenkins.model.BuildDiscarderProperty>      <strategy class=\"hudson.tasks.LogRotator\">        <daysToKeep>-1</daysToKeep>        <numToKeep>10</numToKeep>        <artifactDaysToKeep>-1</artifactDaysToKeep>        <artifactNumToKeep>-1</artifactNumToKeep>      </strategy>    </jenkins.model.BuildDiscarderProperty>    <hudson.model.ParametersDefinitionProperty>      <parameterDefinitions>        <hudson.model.ChoiceParameterDefinition>          <name>Image_Type</name>          <description>Select which image you want to build i.e. both docker and podman, only docker or only podman.</description>          <choices class=\"java.util.Arrays$ArrayList\">            <a class=\"string-array\">              <string>Both</string>              <string>Docker</string>              <string>Podman</string>            </a>          </choices>        </hudson.model.ChoiceParameterDefinition>        <hudson.model.ChoiceParameterDefinition>          <name>Image_Release_Type</name>          <description>Select image name to build.</description>          <choices class=\"java.util.Arrays$ArrayList\">            <a class=\"string-array\">              <string>dmap_app_dev</string>              <string>dmap_app_qa</string>              <string>dmap_app_prod</string>            </a>          </choices>        </hudson.model.ChoiceParameterDefinition>        <hudson.model.StringParameterDefinition>          <name>Image_Version</name>          <description>Enter image version to be build.</description>          <defaultValue>v1.0.0.0</defaultValue>          <trim>false</trim>        </hudson.model.StringParameterDefinition>        <hudson.model.StringParameterDefinition>          <name>Image_Branch_Name</name>          <description>Enter git branch name to pull image build scripts.</description>          <defaultValue>develop</defaultValue>          <trim>false</trim>        </hudson.model.StringParameterDefinition>        <hudson.model.StringParameterDefinition>          <name>service_pack_version</name>          <description>Enter service pack version.</description>          <trim>false</trim>        </hudson.model.StringParameterDefinition>        <hudson.model.StringParameterDefinition>          <name>service_pack_location</name>          <description>Enter service pack location (BLOB/on-prem VM). It should be full path.</description>          <trim>false</trim>        </hudson.model.StringParameterDefinition>      </parameterDefinitions>    </hudson.model.ParametersDefinitionProperty>  </properties>  <definition class=\"org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition\" plugin=\"workflow-cps@3837.v305192405b_c0\">    <script>pipeline {    environment {        APPLICATION_NAME = &quot;Build_DMAP_App_Image&quot;\t\t//NOTIFYUSERS = &apos;abhayj@newtglobalcorp.com&apos;\t\tNOTIFYUSERS = &apos;dmap_dev@newtglobalcorp.com&apos;\t\tBUILD_DETAILS = &quot;&lt;BR&gt;Job Name: ${env.JOB_NAME} &lt;BR&gt;Build Number: ${env.BUILD_NUMBER} &lt;BR&gt;Build URL: ${BUILD_URL}&quot;\t\tPATH = &quot;/home/newtdba/.nvm/versions/node/v18.3.0/bin:/home/newtdba/.local/bin:/home/newtdba/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/home/newtdba/.nvm/versions/node/v16.18.1/bin/ng:/var/lib/jenkins/sonar-scanner-4.8.1.3023/bin:/usr/lib/jvm/java-11-openjdk-11.0.18.0.10-3.el9.x86_64/bin/java:/home/newtdba/.local/bin/bandit:/opt/sonar-scanner/bin:$PATH&quot;        JAVA_HOME = &apos;/usr/lib/jvm/java-11-openjdk-11.0.18.0.10-3.el9.x86_64&apos;    }    parameters {        choice(name: &apos;Image_Type&apos;, choices: [&apos;Both&apos;,&apos;Docker&apos;,&apos;Podman&apos;], description: &apos;Select which image you want to build i.e. both docker and podman, only docker or only podman.&apos;)\t    choice(name: &apos;Image_Release_Type&apos;, choices: [&apos;dmap_app_dev&apos;,&apos;dmap_app_qa&apos;,&apos;dmap_app_prod&apos;], description: &apos;Select image name to build.&apos;)        string(name: &apos;Image_Version&apos;, defaultValue: &apos;v1.0.0.0&apos;, description: &apos;Enter image version to be build.&apos;)\t    string(name: &apos;Image_Branch_Name&apos;, defaultValue: &apos;develop&apos;, description: &apos;Enter git branch name to pull image build scripts.&apos;)        string(name: &apos;service_pack_version&apos;, defaultValue: &apos;&apos;, description: &apos;Enter service pack version.&apos;)        string(name: &apos;service_pack_location&apos;, defaultValue: &apos;&apos;, description: &apos;Enter service pack location (BLOB/on-prem VM). It should be full path.&apos;)    }        agent {        label getNodeLabel()  // Dynamically set node based on user input    }    options {        timestamps()        timeout(time: 5, unit: &apos;HOURS&apos;)        buildDiscarder(logRotator(numToKeepStr: &apos;10&apos;))    }    stages {        stage (&quot;Checkout Code &quot;) {\t\t\tsteps {                dir(&quot;DMAP_App_Docker&quot;) {\t\t\t        deleteDir()\t\t\t\t\techo &quot;Checkout the DMAP_App_Dockerfile&quot;\t\t\t\t\tgit branch: &apos;${Image_Branch_Name}&apos;, credentialsId: &apos;Kavya_1709&apos;, url: &apos;https://github.com/newtglobalgit/DMAP_Extension_Docker_Linux.git&apos;\t\t\t    } \t\t\t}        }    \tstage (&quot;Docker Build&quot;) {\t\t\twhen {                expression {                    return params.Image_Type == &apos;Both&apos; || params.Image_Type == &apos;Docker&apos;                }            }    \t\tsteps {    \t\t    script {\t\t\t\t\tdir(&quot;DMAP_App_Docker&quot;) {\t\t\t\t\t\tscript {\t\t\t\t\t\t\twriteFile(file: &apos;dmap_image_release.yaml&apos;, text:&quot;name: DMAP\\nimage_version: ${params.Image_Version}\\ninitial_service_pack_version: ${params.Service_pack_version}\\n&quot;)\t\t\t\t\t\t\t//Added to Support - Allow user to Enable or Disable Auto Binary Update\t\t\t\t\t\t\twriteFile(file: &apos;dmap_binary_install_mode.yaml&apos;, text:&quot;binary_install_mode: automatic\\n&quot;)\t\t\t\t\t\t\techo &quot;Docker Azure Blob&quot;\t\t\t\t\t\t\twriteFile(file: &apos;dmap_image_release.yaml&apos;, text:&quot;name: DMAP\\nimage_version: ${params.Image_Version}\\ninitial_service_pack_version: ${params.Service_pack_version}\\n&quot;)\t\t\t\t\t\t\t\t\t\t\t\t\t\techo &quot;Build Azure Docker Image&quot;\t\t\t\t\t\t\tsh &quot;&quot;&quot;\t\t\t\t\t\t\tsed -i -e &apos;s#service_pack_location#${params.service_pack_location}#&apos; Dockerfile\t\t\t\t\t\t\t&quot;&quot;&quot;\t\t\t\t\t\t\t\t\t\t\t\t\t\tsh &quot;docker build --no-cache -t ngdmapo/${params.Image_Release_Type}:${params.Image_Version} .&quot;\t\t\t\t\t\t\tsh &quot;docker images&quot;\t\t\t\t\t\t}\t\t\t\t\t}\t\t\t\t}    \t\t}    \t}\t    stage (&quot;Docker Push&quot;) {\t\t\twhen {                expression {                    return params.Image_Type == &apos;Both&apos; || params.Image_Type == &apos;Docker&apos;                }            }    \t\tsteps {    \t\t   \tscript {    \t\t        echo &quot;Push Docker Image&quot;    \t\t\t    withCredentials([usernamePassword(credentialsId: &apos;DockerHubCred&apos;, passwordVariable: &apos;dockerHubPassword&apos;, usernameVariable: &apos;dockerHubUser&apos;)]) {                        sh &quot;docker login -u ${env.dockerHubUser} -p ${env.dockerHubPassword}&quot;    \t\t\t        sh &quot;docker tag ngdmapo/${Image_Release_Type}:${Image_Version} ngdmapo/${Image_Release_Type}&quot;    \t\t\t        sh &quot;docker push ngdmapo/${Image_Release_Type}:${Image_Version}&quot;    \t\t\t        sh &quot;docker push ngdmapo/${Image_Release_Type}&quot;                    }    \t\t\t}    \t    }\t    }\t\tstage (&quot;Podman Build&quot;) {\t\t\twhen {                expression {                    return params.Image_Type == &apos;Podman&apos;                }            }    \t\tsteps {    \t\t    dir(&quot;DMAP_App_Docker&quot;) {\t\t\t\t\tscript {\t\t\t\t\t\twriteFile(file: &apos;dmap_image_release.yaml&apos;, text:&quot;name: DMAP\\nimage_version: ${params.Image_Version}\\ninitial_service_pack_version: ${params.Service_pack_version}\\n&quot;)\t\t\t\t\t\twriteFile(file: &apos;dmap_binary_install_mode.yaml&apos;, text:&quot;binary_install_mode: automatic\\n&quot;)\t\t\t\t\t\techo &quot;Podman Azure Blob&quot;\t\t\t\t\t\twriteFile(file: &apos;dmap_image_release.yaml&apos;, text:&quot;name: DMAP\\nimage_version: ${params.Image_Version}\\ninitial_service_pack_version: ${params.Service_pack_version}\\n&quot;)\t\t\t\t\t\t\t\t\t\t\t\techo &quot;Build Azure Podman Image&quot;\t\t\t\t\t\tsh &quot;&quot;&quot;\t\t\t\t\t\tsed -i -e &apos;s#service_pack_location#${params.service_pack_location}#&apos; Dockerfile\t\t\t\t\t\t&quot;&quot;&quot;\t\t\t\t\t\t\t\t\t\t\t\tsh &quot;podman build --cgroup-manager=cgroupfs -t ngdmapo/${params.Image_Release_Type}:${params.Image_Version} .&quot;\t\t\t\t\t\tsh &quot;podman images&quot;\t\t\t\t\t}\t\t\t    }    \t\t}    \t}\t    stage (&quot;Podman Push&quot;) {\t\t\twhen {                expression {                    return params.Image_Type == &apos;Podman&apos;                }            }    \t\tsteps {    \t\t   script {    \t\t        echo &quot;Push podman Image&quot;    \t\t\t   \twithCredentials([usernamePassword(credentialsId: &apos;podmanHubCred&apos;, passwordVariable: &apos;podmanHubPassword&apos;, usernameVariable: &apos;podmanHubUser&apos;)]) {                        sh &quot;podman login -u ${env.podmanHubUser} -p ${env.podmanHubPassword} quay.io&quot;    \t\t\t    \t\t\t        sh &quot;podman tag localhost/ngdmapo/${Image_Release_Type}:${Image_Version} quay.io/${env.podmanHubUser}/ngdmapo/${Image_Release_Type}:latest&quot;    \t\t\t        sh &quot;podman push quay.io/${env.podmanHubUser}/ngdmapo/${Image_Release_Type}:latest&quot;    \t\t\t        sh &quot;podman tag localhost/ngdmapo/${Image_Release_Type}:${Image_Version} quay.io/${env.podmanHubUser}/ngdmapo/${Image_Release_Type}:${Image_Version}&quot;\t\t\t\t\t\tsh &quot;podman push quay.io/${env.podmanHubUser}/ngdmapo/${Image_Release_Type}:${Image_Version}&quot;    \t\t\t        //sh &quot;podman push quay.io/${env.podmanHubUser}/ngdmapo/${Image_Release_Type}:${Image_Version}&quot;    \t\t\t    }    \t\t\t}    \t    }\t    }    }    post {        always {            emailext attachmentsPattern: &apos;DMAP_Extension_flask/AppBanditAnalysisReport.html,DMAP_Extension_Backend/snyk_report.html,DMAP_Extension_Backend/DMAP_App_Junit_CoverageReport.html,DMAP_Extension_Backend/DMAP_App_Junit_PassFailReport.html,DMAP_Extension_flask/SonarQube/App_Python_Sonar_Issues.xlsx,DMAP_Extension_flask/SonarQube/App_Java_Sonar_Issues.xlsx,DMAP_Extension_flask/ModularTestCoverageReport.html,DMAP_Extension_flask/ModularTestOutput.html&apos;,            subject: &quot;Jenkins Job Report For ${APPLICATION_NAME} - ${currentBuild.currentResult}&quot;,\t\t\tbody: &quot;BUILD DETAILS: ${BUILD_DETAILS}      BUILD STATUS: ${currentBuild.currentResult}&quot;,\t\t\tto: &quot;${NOTIFYUSERS}&quot;            script {                if (params[&apos;Image_Type&apos;] == &apos;Both&apos;) {                    echo &quot;Going to start DMAP App Image pipeline to build App Podman image.&quot;                    // Triggering additional builds after the current build is successful                    build job: &apos;DMAP_App_Image&apos;,                    parameters: [                        string(name: &apos;Image_Type&apos;, value: &apos;Podman&apos;),                        string(name: &apos;Image_Release_Type&apos;, value: params[&apos;Image_Release_Type&apos;]),                        string(name: &apos;Image_Version&apos;, value: params[&apos;Image_Version&apos;]),                        string(name: &apos;Image_Branch_Name&apos;, value: params[&apos;Image_Branch_Name&apos;]),                        string(name: &apos;service_pack_version&apos;, value: params[&apos;service_pack_version&apos;]),                        string(name: &apos;service_pack_location&apos;, value: params[&apos;service_pack_location&apos;])                    ],                    wait: false                }            }        }    }}def getNodeLabel() {    if (params.Image_Type == &apos;Docker&apos; || params.Image_Type == &apos;Both&apos;) {        return &apos;build_slave&apos;    } else if (params.Image_Type == &apos;Podman&apos;) {        return &apos;build_slave_podman&apos;    }}</script>    <sandbox>true</sandbox>  </definition>  <triggers/>  <disabled>false</disabled></flow-definition>";

        Map<String, Integer> commandLines = extractCommandsWithLineNumbers(scriptContent);
        for (Map.Entry<String, Integer> entry : commandLines.entrySet()) {
            System.out.println("Found command: " + entry.getKey() + " at line: " + entry.getValue());
        }
    }

    private static Map<String, Integer> extractCommandsWithLineNumbers(String scriptContent) {
        Map<String, Integer> commandsWithLines = new HashMap<>();
        try {
            // Split the script content into lines
            String[] lines = scriptContent.split("\\n");
            // Use regex to extract commands from each line and capture line numbers
            Pattern pattern = Pattern.compile("\\b(mvn|gradle|python|javac|java|npm|ng|vue|django|flask|rails|spring-boot|dotnet|flutter|react-native|ionic)\\b\\s+[^\\n]+");
            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = pattern.matcher(lines[i]);
                while (matcher.find()) {
                    commandsWithLines.put(matcher.group(), i + 1); // Line numbers are 1-based
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commandsWithLines;
    }
}
