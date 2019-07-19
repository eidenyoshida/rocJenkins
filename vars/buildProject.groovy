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
    def action =
    {key ->
        def platform = dockerArray[key]

        node (platform.jenkinsLabel)
        {
            ansiColor('vga')
            {
                try
                {
                    stage ("Docker " + "${platform.jenkinsLabel}") 
                    {
                        timeout(time: project.timeout.docker, unit: 'HOURS')
                        {
                            build.checkout(project.paths)
                    
                            if(env.MULTI_GPU == '1')
                            {
                                String maskNum = env.EXECUTOR_NUMBER
                                String gpuMask = 'DOCKER_GPU_MASK_'+maskNum
                                platform.runArgs += ' ' + env[gpuMask]
                                echo platform.runArgs
                            }

                            platform.buildImage(this)
                        }
                    }
                    if (formatCheck && !platform.jenkinsLabel.contains('hip-clang'))
                    {
                        stage ("Format Check " + "${platform.jenkinsLabel}")
                        {
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
                            | grep -v 'build/' \
                            | xargs -n 1 -P 1 -I{} -t sh -c \'/opt/rocm/hcc/bin/clang-format -style=file {} | diff - {}\'
                            """

                            platform.runCommand(this, formatCommand)
                        }   
                    }
                    stage ("Compile " + "${platform.jenkinsLabel}")
                    {  
                        try 
                        {
                            timeout(time: project.timeout.compile, unit: 'HOURS')
                            {
                                compileCommand.call(platform,project)
                            }
                        }
                        catch(e)
                        {
                            if(platform.jenkinsLabel.contains('hip-clang'))
                            {
                                //hip-clang is experimental for now
                                currentBuild.result = 'UNSTABLE'
                            }
                            throw e
                        }
                    }
                    if(testCommand != null)
                    {
                        stage ("Test " + "${platform.jenkinsLabel}")
                        {
                            try
                            {
                                timeout(time: project.timeout.test, unit: 'HOURS')
                                {   
                                    testCommand.call(platform, project)
                                }
                            }
                            catch(e)
                            {        
                                if(platform.jenkinsLabel.contains('hip-clang'))
                                {
                                    //hip-clang is experimental for now
                                    currentBuild.result = 'UNSTABLE'
                                }
                                throw e
                            }
                        }
                    }
                    if(packageCommand != null)
                    {
                        stage ("Package " + "${platform.jenkinsLabel}")
                        {
                            packageCommand.call(platform, project)
                        }
                    }
                    if(platform.jenkinsLabel.contains('centos'))
                    {
                        //This is temporary until CentOS 7 images support GPU access for the Jenkins user
                        stage ("Permissions " + "${platform.jenkinsLabel}")
                        {
                            permissionsCommand = "sudo chown jenkins -R ./*"
                        
                            platform.runCommand(this, permissionsCommand)
                        }
                    }
                }
                catch(e)
                {
                    stage("Mail " + "${platform.jenkinsLabel}")
                    {
                        if(platform.jenkinsLabel.contains('hip-clang'))
                        {
                            mail(
                                bcc: '',
                                body: """
                                        Job: ${platform.jenkinsLabel} 
                                        Node: ${env.NODE_NAME}
                                        Please go to http://hsautil.amd.com/job/ROCmSoftwarePlatform/job/${project.name} to view the error
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
                        else
                        {                    
                            mail(
                                bcc: '',
                                body: """
                                        Job: ${platform.jenkinsLabel} 
                                        Node: ${env.NODE_NAME}
                                        Please go to http://hsautil.amd.com/job/ROCmSoftwarePlatform/job/${project.name} to view the error
                                    """,
                                cc: '',
                                charset: 'UTF-8',
                                from: 'dl.mlse.lib.jenkins@amd.com',
                                mimeType: 'text/html',
                                replyTo: '',
                                subject: "${project.name} ${env.BRANCH_NAME} build #${env.BUILD_NUMBER} status is ${currentBuild.result}",       
                                to: "dl.${project.name}-ci@amd.com"
                            )
                        }
                    }
                    throw e
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
