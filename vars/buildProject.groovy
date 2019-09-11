/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker
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
    String reason
    String stageTime
    String rocmBuildId
    int startTime = 0
    int compileTime = 0
    int compileEndTime = 0
    int compileDuration = 0
    int failTime = 0
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
                            startTime = (int)System.currentTimeMillis().intdiv(1000)
                            timeout(time: project.timeout.docker, unit: 'HOURS')
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
                        catch(e)
                        {
                            failTime = (int)System.currentTimeMillis().intdiv(1000)
                            duration = failTime-startTime
                            failedStage = stages[0]
                            if(duration <= 15)
                            {
                                reason = "Could not resolve host github.com, Git checkout/SCM failure"
                            }
                            else if(duration >= 175 && duration <= 185)
                            {
                                reason = "Docker container could not be launched as system is low on memory"
                            } 
                            else if(duration >= project.timeout.docker*3420)
                            {
                                reason = "Timeout due to loss of connection to the node, Authentication issues"
                            }
                            else
                            {
                                reason = "Problems building/running docker container, Permissions or dependency issues in container"
                            }

                            if(platform.jenkinsLabel.contains('hip-clang'))
                            {
                                //hip-clang is experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            else
                            {
                                currentBuild.result = 'FAILURE'
                                throw e
                            } 
                        }
                    }
                    if (formatCheck && !platform.jenkinsLabel.contains('hip-clang'))
                    {
                        try
                        {
                            stage ("${stages[1]}${platform.jenkinsLabel}")
                            {
                                startTime = (int)System.currentTimeMillis().intdiv(1000)
                                
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
                        catch(e)
                        {
                            failTime = (int)System.currentTimeMillis().intdiv(1000)
                            duration = failTime-startTime
                            failedStage = stages[1]
                            if(duration >= 175 && duration <= 185)
                            {
                                reason = "Docker container could not be launched as system is low on memory"
                            }
                            else
                            { 
                                reason = "Not running /opt/rocm/hcc/bin/clang-format with the latest ROCm release before committing code"
                            }
                            currentBuild.result = 'FAILURE'
                            throw e
                        }   
                    }
                    stage ("${stages[2]}${platform.jenkinsLabel}")
                    {  
                        try 
                        {
                            compileTime = (int)System.currentTimeMillis().intdiv(1000)
                            
                            timeout(time: project.timeout.compile, unit: 'HOURS')
                            {
                                compileCommand.call(platform,project)
                                compileEndTime = (int)System.currentTimeMillis().intdiv(1000)
                            }
                        }
                        catch(e)
                        {
                            compileEndTime = (int)System.currentTimeMillis().intdiv(1000)
                            duration = compileEndTime-compileTime
                            failedStage = stages[2]

                            if(duration >= project.timeout.compile*3420)
                            {
                                reason = "${project.name} build script timed out"
                            }
                            else if(duration >= 175 && duration <= 185)
                            {
                                reason = "Docker container could not be launched as system is low on memory"
                            } 
                            else if(project.name == 'Tensile')
                            {
                                reason = "Lint or host test failures on ${platform.gpuLabel(platform.jenkinsLabel)}"
                            }
                            else
                            {
                                reason = "CMake/path issues, Broken/incompatible compiler, Permissions"
                            }

                            if(platform.jenkinsLabel.contains('hip-clang'))
                            {
                                //hip-clang is experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            else
                            {
                                currentBuild.result = 'FAILURE'
                                throw e
                            }
                        }
                    }
                    if(testCommand != null)
                    {
                        stage ("${stages[3]}${platform.jenkinsLabel}")
                        {
                            try
                            {
                                startTime = (int)System.currentTimeMillis().intdiv(1000)
                                timeout(time: project.timeout.test, unit: 'HOURS')
                                {   
                                    testCommand.call(platform, project)
                                }
                            }
                            catch(e)
                            {        
                                failTime = (int)System.currentTimeMillis().intdiv(1000)
                                duration = failTime-startTime
                               
                                if(duration <= 60)
                                {
                                    failedStage = stages[2]
                                    reason = "CMake/path issues, Broken/incompatible compiler, Permissions"
                                    duration = compileDuration
                                }
                                else
                                {
                                    failedStage = stages[3]
                                    if(duration >= 175 && duration <= 185)
                                    {
                                        reason = "Docker container could not be launched as system is low on memory"
                                    } 
                                    else if(duration >= project.timeout.test*3420)
                                    {
                                        reason = "Timeout due to stalled tests on ${platform.gpuLabel(platform.jenkinsLabel)}"
                                    }
                                    else
                                    {
                                        reason = "Failed tests on ${platform.gpuLabel(platform.jenkinsLabel)} "
                                    }
                                }

                                if(platform.jenkinsLabel.contains('hip-clang') ||
				   platform.jenkinsLabel.contains('sles'))
                                {
                                    //hip-clang is experimental for now
                                    currentBuild.result = 'UNSTABLE'
                                }
                                else
                                {
                                    currentBuild.result = 'FAILURE'
                                    throw e
                                }
                            }
                        }
                    }
                    if(packageCommand != null)
                    {
                        try
                        {
                            timeout(time: project.timeout.docker, unit: 'HOURS')
                            {
                                stage ("${stages[4]}${platform.jenkinsLabel}")
                                {
                                    startTime = (int)System.currentTimeMillis().intdiv(1000)
                                    packageCommand.call(platform, project)
                                }
                            }
                        }
                        catch(e)
                        {
                            failTime = (int)System.currentTimeMillis().intdiv(1000)
                            duration = failTime-startTime
                            failedStage = stages[4]
                    
                            if(platform.jenkinsLabel.contains('centos') || platform.jenkinsLabel.contains('sles'))
                            {
                                reason = "CentOS-related packaging error"
                            }
                            else if(duration >= 175 && duration <= 185)
                            {
                                reason = "Docker container could not be launched as system is low on memory"
                            } 
                            else
                            {
                                reason = "Trying to make a package in the incorrect directory"
                            }

                            if(platform.jenkinsLabel.contains('hip-clang'))
                            {
                                //hip-clang is experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            else
                            {
                                currentBuild.result = 'FAILURE'
                                throw e
                            }
                        }
                    }
                    if(platform.jenkinsLabel.contains('centos') || platform.jenkinsLabel.contains('sles') || platform.jenkinsLabel.contains('hip-clang'))
                    { 
                        stage ("${stages[5]}${platform.jenkinsLabel}")
                        {
                            try
                            {
                                timeout(time: project.timeout.docker, unit: 'HOURS')
                                {
                                    startTime = (int)System.currentTimeMillis().intdiv(1000)
                                    permissionsCommand = "sudo chown jenkins -R ./*"
                                
                                    platform.runCommand(this, permissionsCommand)
                                }
                            }
                            catch(e)
                            {
                                failTime = (int)System.currentTimeMillis().intdiv(1000)
                                duration = failTime-startTime
                                failedStage = stages[5]
                                
                                if(duration >= 175 && duration <= 185)
                                {
                                    reason = "Docker container could not be launched as system is low on memory"
                                }
                                else
                                { 
                                    reason = "Incorrect user/group permissions for Jenkins user"
                                }

                                currentBuild.result = 'FAILURE'
                                throw e
                            }
                        }
                    }
                    if((platform.jenkinsLabel.contains('hip-clang')
		        || platform.jenkinsLabel.contains('sles'))
		    	&& currentBuild.result == 'UNSTABLE')
                    {
                        stage("${stages[6]}${platform.jenkinsLabel}")
                        {
                            if(env.BRANCH_NAME.contains('PR'))
                            {
                                link = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}"
                                log = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/consoleText"
                                stageView = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/view/change-requests/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/flowGraphTable"
                                blueOcean = "${env.JENKINS_URL}blue/organizations/jenkins/ROCmSoftwarePlatform%2F${project.name}/detail/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/pipeline"
                            }
                            else
                            {
                                link = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}"
                                log = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/consoleText"
                                stageView = "${env.JENKINS_URL}job/ROCmSoftwarePlatform/job/${project.name}/job/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/flowGraphTable"              
                                blueOcean = "${env.JENKINS_URL}blue/organizations/jenkins/ROCmSoftwarePlatform%2F${project.name}/detail/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/pipeline"
                            }
                      
                            stageTime = platform.timeFunction(duration)
 
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
                                to: "akila.premachandra@amd.com"
                            )
                        }
                    }
                }
                catch(e)
                {
                    if((!platform.jenkinsLabel.contains('hip-clang') || !platform.jenkinsLabel.contains('sles')) && currentBuild.result == 'FAILURE')
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
                            
                            stageTime = platform.timeFunction(duration)
                            
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
                            
                            throw e
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
