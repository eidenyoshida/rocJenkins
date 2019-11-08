/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker

import java.nio.file.Path;

import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages

def call(rocProject project, boolean formatCheck, def dockerArray, def compileCommand, def testCommand, def packageCommand)
{
    if (!currentBuild.rawBuild.getAction(ExecutionModelAction))
        currentBuild.rawBuild.addAction(new ExecutionModelAction(new ModelASTStages(null)))

    String[] stages = ['Docker ', 'Format Check ', 'Compile ', 'Test ', 'Package ', 'Permissions ', 'Mail ']
    
    def action =
    {key ->
        def platform = dockerArray[key]

        node (platform.jenkinsLabel)
        {
            stage ("${platform.jenkinsLabel}")
            {
                for (int i = 0; i < stages.size(); i++) {
                    stage ("${stages[i]}") 
			        {
                        println("RUNNING " + platform.jenkinsLabel + " " + stages[i])
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