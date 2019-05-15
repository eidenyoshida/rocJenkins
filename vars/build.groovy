/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */
import com.amd.project.project_paths;
import java.nio.file.Path;

////////////////////////////////////////////////////////////////////////
// -- BUILD RELATED FUNCTIONS

////////////////////////////////////////////////////////////////////////
// Checkout source code, source dependencies and update version number numbers
// Returns a relative path to the directory where the source exists in the workspace
void checkout( project_paths paths )
{
    paths.project_src_prefix = paths.src_prefix + '/' + paths.project_name

    dir( paths.project_src_prefix )
    {
    // checkout project
    checkout([
        $class: 'GitSCM',
        branches: scm.branches,
        doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
        extensions: scm.extensions + [[$class: 'CleanCheckout']] + [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false]],
        userRemoteConfigs: scm.userRemoteConfigs
        ])

    paths.construct_build_prefix()
    }
}
