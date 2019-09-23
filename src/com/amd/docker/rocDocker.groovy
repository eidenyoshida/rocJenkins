/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.docker

import com.amd.project.*
import java.nio.file.Path;

// Docker related variables gathered together to reduce parameter bloat on function calls
class rocDocker implements Serializable
{
    String baseImage
    String buildDockerfile
    String installDockerfile
    String runArgs
    String buildArgs
    String buildImageName
    String jenkinsLabel
    String executorNumber

    def infoCommands   
    def image
    def paths

    String listCPUs(def lowerBound, def upperBound, def stage)
    {
        String cpuRange = ""
        stage.dir( paths.project_src_prefix )
        {
            stage.sh(script: 'lstopo --only PU lstopo.txt --of console', returnStdout: false )    
    
            for(int id= lowerBound.toInteger(); id<= upperBound.toInteger(); id++)
            {
                cpuRange += stage.sh(script: "cat lstopo.txt | grep \"L#$id \" | awk \'{print \$3}\'| grep -o -E \"[0-9]+\" ", returnStdout: true ).trim()

                if(id != upperBound.toInteger())
                {
                    cpuRange += ','
                }
            }
        }
        return cpuRange
    }

    void buildImage(def stage)
    {           
        stage.dir( paths.project_src_prefix )
        {
            def user_uid = stage.sh(script: 'id -u', returnStdout: true ).trim()
    
            def systemCPUs = stage.sh(script: 'nproc', returnStdout: true ).trim()
            def CPUsPerExecutor = systemCPUs.toInteger().intdiv((stage.env.NUMBER_OF_EXECUTORS).toInteger())
            def containerCPUs_low = (stage.env.EXECUTOR_NUMBER).toInteger() * CPUsPerExecutor
            def containerCPUs_high = containerCPUs_low + CPUsPerExecutor - 1
            
            String containerRange = listCPUs(containerCPUs_low,containerCPUs_high, stage)

            runArgs += " --cpuset-cpus=\"${containerRange}\""       

            String imageLabel = jenkinsLabel.replaceAll("\\W","")

            // Docker 17.05 introduced the ability to use ARG values in FROM statements
            // Docker inspect failing on FROM statements with ARG https://issues.jenkins-ci.org/browse/JENKINS-44836
            stage.docker.build( "${paths.project_name}/${buildImageName}/${imageLabel}:latest", 
                               "--pull -f docker/${buildDockerfile} --build-arg user_uid=${user_uid} --build-arg base_image=${baseImage} --cpuset-cpus=\"${containerRange}\" .")

            // JENKINS-44836 workaround by using a bash script instead of docker.build()
            //stage.sh "docker build -t ${paths.project_name}/${buildImageName}/${imageLabel}:latest -f docker/${buildDockerfile} ${buildArgs} --build-arg user_uid=${user_uid} --build-arg base_image=${baseImage} ."

            image = stage.docker.image( "${paths.project_name}/${buildImageName}/${imageLabel}:latest" )
            
            // Print system information for the log
            image.inside( runArgs )
            {
                // Temporary workaround to access GPU in sles container
                if(jenkinsLabel.contains('sles') || (jenkinsLabel.contains('centos7') && jenkinsLabel.contains('hip-clang')))
                {
                    stage.sh(script: 'sudo chgrp -R video /dev/dri', returnStdout: false)
                }
                stage.sh(infoCommands)
            }
        }
    }

    void runCommand(def stage, def command)
    {
        image.inside(runArgs)
        {
            stage.sh(command)
        }
    }

    void archiveArtifacts(def stage, String artifactName)
    {
        image.inside(runArgs)
        {
            stage.archiveArtifacts artifacts: artifactName, fingerprint: true
        }
    }

    def makePackage(String label, String directory, boolean clientPackaging = false, boolean sudo = false) 
    {
        String permissions = ''
        String query = ":"
        String client = ":"
        String fileType
        
        if(label.contains('ubuntu') || label.contains('debian'))
        {
            fileType = 'deb'
        }
        else
        {
            fileType = 'rpm'
        }
    
        if(sudo == true) permissions = 'sudo'

        if(clientPackaging == true)
        {
            client = """
                    ${permissions} make package_clients
                    ${permissions} mv clients/*.${fileType} package/
                """
        }

        if(fileType == 'rpm')
        {
            query = "${permissions} ${fileType} -qlp package/*.${fileType}"
        }
        else
        {
            query = "${permissions} dpkg -c package/*.deb"
        }
        
        def command = """
                    set -x
                    cd ${directory}
                    ${permissions} make package
                    ${permissions} mkdir -p package
                    ${permissions} mv *.${fileType} package/
                    ${query}
                    ${client}
                """
        
        return [command, "${directory}/package/*.${fileType}"]
    }
    
    def buildRocblas(String directory, String label)
    {
        String compiler = 'hcc'
        String ld = 'hcc/lib'
        String buildCommand = './install.sh -lasm_ci -c'

        if(label.contains('hip-clang')) 
        {
            compiler = 'hipcc'
            ld = 'lib'
            buildCommand += ' --hip-clang'
        }

        return """#!/usr/bin/env bash
                set -x
                cd ${directory}/../rocblas
                LD_LIBRARY_PATH=/opt/rocm/${ld} CXX=/opt/rocm/bin/${compiler} ${buildCommand} -t${directory}
            """
    }

    def testRocblas(String testType, String directory, String label)
    {
        String sudo = ''
        String ld = 'hcc/lib'
        String filter = ''
        String sscal = ":"

        if(label.contains('hip-clang')) ld = 'lib'
        if(label.contains('centos')) sudo = 'sudo'
        if(testType == 'checkin')        
        {
            sscal = "LD_LIBRARY_PATH=/opt/rocm/${ld} ${sudo} ./example-sscal
            filter = 'quick*:*pre_'
        }

        return """#!/usr/bin/env bash
                set -x
                cd ${directory}
                ${sscal}
                LD_LIBRARY_PATH=/opt/rocm/${ld} GTEST_LISTENER=NO_PASS_LINE_IN_LOG ${sudo} ./rocblas-test --gtest_output=xml --gtest_color=yes --gtest_filter=*${filter}${testType}*-*known_bug* #--gtest_filter=*${testType}*
            """
    }

/*    
    void UploadDockerHub(String RemoteOrg)
    {
    // Do not treat failures to push to docker-hub as a build fail
    try
    {
        sh  """#!/usr/bin/env bash
          set -x
          echo inside sh
          docker tag ${local_org}/${image_name} ${remote_org}/${image_name
        """

        docker_hub_image = image( "${remote_org}/${image_name}" )

        docker.withRegistry('https://registry.hub.docker.com', 'docker-hub-cred' )
        {
        docker_hub_image.push( "${env.BUILD_NUMBER}" )
        docker_hub_image.push( 'latest' )
        }
    }
    catch( err )
    {
        currentBuild.result = 'SUCCESS'
    }
    }
*/
