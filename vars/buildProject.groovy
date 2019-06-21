/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker
import java.nio.file.Path;

def call(rocProject project, boolean formatCheck, def dockerArray, def compileCommand, def testCommand, def packageCommand)
{
    def action =
    {key ->
        def platform = dockerArray[key]

        node (platform.jenkinsLabel)
        {
            ansiColor('vga')
            {
                stage ("Docker " + "${platform.jenkinsLabel}") 
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
                    timeout(time: project.timeout.compile, unit: 'HOURS')
                    {
                        compileCommand.call(platform,project)
                    }
                }
                stage ("Test " + "${platform.jenkinsLabel}")
                {
                    timeout(time: project.timeout.test, unit: 'HOURS')
                    {
                        testCommand.call(platform, project)
                    }
                }
                if(packageCommand != null)
                {
                    stage ("Package " + "${platform.jenkinsLabel}")
                    {
                        packageCommand.call(platform, project)
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
