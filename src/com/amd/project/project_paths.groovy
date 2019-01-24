/* ************************************************************************
 * Copyright 2018 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.project

// Paths variables bundled together to reduce parameter bloat on function calls
class project_paths implements Serializable
{
    String project_name
    String src_prefix ='src'
    String project_src_prefix
    String build_prefix = 'src'
    String project_build_prefix 
    String build_command = './install -c' 
    String test_command
    String test_command_nightly
    
    void construct_build_prefix()
    {
        project_build_prefix = build_prefix + '/' + project_name;
    }
    
}
