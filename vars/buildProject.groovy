/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker
import com.amd.mail.*
import java.nio.file.Path;

import hudson.*
import jenkins.*

def call(rocProject project, boolean formatCheck, def dockerArray, def compileCommand, def testCommand, def packageCommand)
{
    String link
    String log
    String stageView
    String blueOcean
    String recipient
    String[] stages = ['Docker ', 'Format Check ', 'Compile ', 'Test ', 'Package ', 'Permissions ', 'Mail ']
    String failedStage
    String lastBuildResult = null
    String stageTime
    String rocmBuildId
    int startTime = 0
    int compileTime = 0
    int compileDuration = 0
    int duration = 0
    
    def action =
    {key ->
        def platform = dockerArray[key]
        
        node (platform.jenkinsLabel)
        {
            ansiColor('vga')
            {
                try
                {
                    stage ("${stages[0]}${platform.jenkinsLabel}") 
                    {
                        try
                        {
                            startTime = project.email.start()
                            if(currentBuild.getPreviousBuild() != null)
                            {
                                lastBuildResult = currentBuild.getPreviousBuild().result  
                            }

                            timeout(time: project.timeout.docker, unit: 'MINUTES')
                            {
                                rocmBuildId = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
                                env.ROCM_BUILD_ID = rocmBuildId
                                platform.executorNumber = env.EXECUTOR_NUMBER
                                build.checkout(project.paths)
                        
                                if(env.MULTI_GPU == '1')
                                {
                                    String maskNum = env.EXECUTOR_NUMBER
                                    String gpuMask = 'DOCKER_GPU_MASK_'+maskNum
                                    platform.runArgs += ' ' + env[gpuMask]
                                }
                                else
                                {
                                    platform.runArgs +=  " --device=/dev/dri"  
                                }
                                echo platform.runArgs
                                platform.buildImage(this)
                                informationalCommand = """
                                                        nproc
                                                       """
                                platform.runCommand(this, informationalCommand)
                            }
                        }
                        catch(Exception e)
                        {
                            duration = project.email.stop(startTime)
                            failedStage = stages[0]
                            if(platform.jenkinsLabel.contains('hip-clang') || platform.jenkinsLabel.contains('sles'))
                            {
                                //hip-clang and sles are experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            else
                            {
                                currentBuild.result = 'FAILURE'
                            } 
                            throw e
                        }
                    }
                    if (formatCheck && !platform.jenkinsLabel.contains('hip-clang'))
                    {
                        try
                        {
                            stage ("${stages[1]}${platform.jenkinsLabel}")
                            {
                                timeout(time: project.timeout.format, unit: 'MINUTES')
                                {
                                    startTime = project.email.start()
                                    
                                    formatCommand = """
                                    /opt/rocm/hcc/bin/clang-format --version;
                                    cd ${project.paths.project_build_prefix};
                                    /opt/rocm/hcc/bin/clang-format -style=file -dump-config;
                                    find . -iname \'*.h\' \
                                        -o -iname \'*.hpp\' \
                                        -o -iname \'*.cpp\' \
                                        -o -iname \'*.h.in\' \
                                        -o -iname \'*.hpp.in\' \
                                        -o -iname \'*.cpp.in\' \
                                    | grep -v -e 'build/' -e 'extern/' \
                                    | xargs -n 1 -P 1 -I{} -t sh -c \'/opt/rocm/hcc/bin/clang-format -style=file {} | diff - {}\'
                                    """

                                    platform.runCommand(this, formatCommand)
                                }
                            }
                        }
                        catch(Exception e)
                        {
                            duration = project.email.stop(startTime)
                            failedStage = stages[1]
                            currentBuild.result = 'FAILURE'
                            throw e
                        }   
                    }
                    stage ("${stages[2]}${platform.jenkinsLabel}")
                    {  
                        try 
                        {
                            compileTime = project.email.start()
                            timeout(time: project.timeout.compile, unit: 'MINUTES')
                            {
                                compileCommand.call(platform,project)
                                compileDuration = project.email.stop(compileTime)
                            }
                        }
                        catch(Exception e)
                        {
                            duration = project.email.stop(compileTime)
                            failedStage = stages[2]
                            if(platform.jenkinsLabel.contains('hip-clang') || platform.jenkinsLabel.contains('sles'))
                            {
                                //hip-clang and sles are experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            else
                            {
                                currentBuild.result = 'FAILURE'
                            }
                            throw e
                        }
                    }
                    if(testCommand != null)
                    {
                        stage ("${stages[3]}${platform.jenkinsLabel}")
                        {
                            try
                            {
                                startTime = project.email.start()
                                timeout(time: project.timeout.test, unit: 'MINUTES')
                                {   
                                    testCommand.call(platform, project)
                                }
                            }
                            catch(Exception e)
                            {        
                                duration = project.email.stop(startTime)
                                failedStage = stages[3]
                                if(duration <= 60)
                                {
                                    failedStage = stages[2]
                                    duration = compileDuration
                                }

                                if(platform.jenkinsLabel.contains('hip-clang') || platform.jenkinsLabel.contains('sles'))
                                {
                                    //hip-clang and sles are experimental for now
                                    currentBuild.result = 'UNSTABLE'
                                }
                                else
                                {
                                    currentBuild.result = 'FAILURE'
                                }
                                throw e
                            }
                        }
                    }
                    if(packageCommand != null)
                    {
                        try
                        {
                            timeout(time: project.timeout.packaging, unit: 'MINUTES')
                            {
                                stage ("${stages[4]}${platform.jenkinsLabel}")
                                {
                                    startTime = project.email.start()
                                    packageCommand.call(platform, project)
                                }
                            }
                        }
                        catch(Exception e)
                        {
                            duration = project.email.stop(startTime)
                            failedStage = stages[4]
                            if(platform.jenkinsLabel.contains('hip-clang') || platform.jenkinsLabel.contains('sles'))
                            {
                                //hip-clang and sles are experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            else
                            {
                                currentBuild.result = 'FAILURE'
                            }
                            throw e
                        }
                    }
                    if(platform.jenkinsLabel.contains('centos') || platform.jenkinsLabel.contains('sles')) 
                    { 
                        stage ("${stages[5]}${platform.jenkinsLabel}")
                        {
                            try
                            {
                                timeout(time: project.timeout.permissions, unit: 'MINUTES')
                                {
                                    startTime = project.email.start()
                                    permissionsCommand = "sudo chown jenkins -R ./*"
                                    platform.runCommand(this, permissionsCommand)
                                }
                            }
                            catch(Exception e)
                            {
                                duration = project.email.stop(startTime)
                                failedStage = stages[5]
                                currentBuild.result = 'FAILURE'
                                throw e
                            }
                        }
                    }
                    stage("${stages[6]}${platform.jenkinsLabel}")
                    {
                        if(env.BRANCH_NAME.contains('PR'))
                        {
                            link = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}"
                            log = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/consoleText"
                            stageView = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/flowGraphTable"
                            blueOcean = "${env.JENKINS_URL}blue/organizations/jenkins/ROCmSoftwarePlatform%2F${project.name}/detail/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/pipeline"
                            recipient = "dl.rocjenkins-ci@amd.com"
                        }
                        else
                        {
                            link = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}"
                            log = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/consoleText"
                            stageView = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/flowGraphTable"              
                            blueOcean = "${env.JENKINS_URL}blue/organizations/jenkins/ROCmSoftwarePlatform%2F${project.name}/detail/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/pipeline"
                            recipient = "dl.${project.name}-ci@amd.com"
                        }
                  
                        stageTime = project.email.timeFunction(duration)
                        
                        if(platform.jenkinsLabel.contains('hip-clang'))
                        {
                            recipient = 'akila.premachandra@amd.com'
                        }
                        
                        if(lastBuildResult == 'FAILURE' || lastBuildResult == 'UNSTABLE' || lastBuildResult == null)
                        {
                            currentBuild.result = 'SUCCESS'
                            mail(
                                bcc: '',
                                body: """
                                        Job: ${platform.jenkinsLabel}
                                        <br>Node: ${env.NODE_NAME}
                                        <br><br>View ${project.name} ${env.BRANCH_NAME}:    ${link}
                                        <br>View the full log:  ${log}
                                        <br>View pipeline steps:    ${stageView}
                                        <br>View in Blue Ocean:    ${blueOcean}
                                    """,
                                cc: '',
                                charset: 'UTF-8',
                                from: 'dl.mlse.lib.jenkins@amd.com',
                                mimeType: 'text/html',
                                replyTo: '',
                                subject: "${project.name} ${env.BRANCH_NAME} build #${env.BUILD_NUMBER} status is ${currentBuild.result}",       
                                to: recipient
                            )
                        }
                    }
                }
                catch(Exception e)
                {
                    stage("${stages[6]}${platform.jenkinsLabel}")
                    {
                        if(env.BRANCH_NAME.contains('PR'))
                        {
                            link = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}"
                            log = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/consoleText"
                            stageView = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/flowGraphTable"
                            blueOcean = "${env.JENKINS_URL}blue/organizations/jenkins/ROCmSoftwarePlatform%2F${project.name}/detail/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/pipeline"
                            recipient = "dl.rocjenkins-ci@amd.com"
                        }
                        else
                        {
                            link = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}"
                            log = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/consoleText"
                            stageView = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/flowGraphTable"              
                            blueOcean = "${env.JENKINS_URL}blue/organizations/jenkins/ROCmSoftwarePlatform%2F${project.name}/detail/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/pipeline"
                            recipient = "dl.${project.name}-ci@amd.com"
                        }
                  
                        stageTime = project.email.timeFunction(duration)
                        
                        if(platform.jenkinsLabel.contains('hip-clang'))
                        {
                            recipient = 'akila.premachandra@amd.com'
                        }
                        
                        if(currentBuild.result == 'FAILURE' || currentBuild.result == 'UNSTABLE')
                        {
                            mail(
                                bcc: '',
                                body: """
                                        Job: ${platform.jenkinsLabel}
                                        <br>Failed Stage: ${failedStage}
                                        <br>Time elapsed in Failed Stage: ${stageTime}
                                        <br>Node: ${env.NODE_NAME}
                                        <br><br>View ${project.name} ${env.BRANCH_NAME}:    ${link}
                                        <br>View the full log:  ${log}
                                        <br>View pipeline steps:    ${stageView}
                                        <br>View in Blue Ocean:    ${blueOcean}
                                    """,
                                cc: '',
                                charset: 'UTF-8',
                                from: 'dl.mlse.lib.jenkins@amd.com',
                                mimeType: 'text/html',
                                replyTo: '',
                                subject: "${project.name} ${env.BRANCH_NAME} build #${env.BUILD_NUMBER} status is ${currentBuild.result}",       
                                to: recipient
                            )
                            if(currentBuild.result == 'FAILURE')
                            {
                                throw e
                            }
                        }
                    }
                }
            }
        }
    }
    
    actions = [:]
    for (platform in dockerArray)
    {
        actions[platform.key] = action.curry(platform.key)
    }

    parallel actions
}
