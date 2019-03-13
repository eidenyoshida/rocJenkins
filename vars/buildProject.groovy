/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker
import java.nio.file.Path;

def call(rocProject project, boolean formatCheck, def dockerArray, def compileCommand, def testCommand, def packageCommand)
{
    def action =
    { key ->
        def platform = dockerArray[key]

        node (platform.jenkinsLabel)
        {
            stage ("Docker " + "${platform.jenkinsLabel}") 
            {
                build.checkout(project.paths)
                platform.buildImage(this)
            }
            if (formatCheck)
	    {
		stage ("Format Check " + "${platform.jenkinsLabel}")
		{
                
		    formatCommand =  '''
                    find . -iname \'*.h\' \
                        -o -iname \'*.hpp\' \
                        -o -iname \'*.cpp\' \
                        -o -iname \'*.h.in\' \
                        -o -iname \'*.hpp.in\' \
                        -o -iname \'*.cpp.in\' \
                    | grep -v 'build/' \
                    | xargs -n 1 -P 1 -I{} -t sh -c \'clang-format-3.8 -style=file {} | diff - {}\'
                    '''
		    platform.runCommand(this, formatCommand)
            }
            }
            stage ("Compile " + "${platform.jenkinsLabel}")
            {            
                compileCommand.call(platform,project)
            }
            
            stage ("Test " + "${platform.jenkinsLabel}")
            {
                testCommand.call(platform, project)
            }
            
            stage ("Package " + "${platform.jenkinsLabel}")
            {
                packageCommand.call(platform, project)
            }
        }
    }

    actions = [:]
    for (platform in dockerArray)
    {
        actions[platform.key] = action.curry(platform.key)
    }

    parallel actions

/*    pipeline
    {
        agent { label "master"}

        stages
        {
            stage ("Docker")
            {
                steps
                {
                    script
                    {
                        runParallelStage(project, dockerArray)
                        {
                            platform, runCommand ->
                            build.checkout(project.paths)
                            platform.buildImage(this)
                        }
                    }
                }
            }

            stage ("Compile")
            {
                steps
                {
                    script
                    {
                        runParallelStage(project, dockerArray, compileCommand)
                        {
                            platform, runCommand ->
                            runCommand.call(platform,project)
                        }
                    }
                }
            }

            stage ("Test")
            {
                steps
                {
                    script
                    {
                        runParallelStage(project, dockerArray, testCommand)
                        {
                            platform, runCommand ->
                            runCommand.call(platform,project)
                        }
                    }
                }
            }

            stage ("Package")
            {
                steps
                {
                    script
                    {
                        runParallelStage(project, dockerArray, packageCommand)
                        {
                            platform, runCommand ->
                            runCommand.call(platform, project)
                        }
                    }
                }
            }
        }
    }
*/
}
