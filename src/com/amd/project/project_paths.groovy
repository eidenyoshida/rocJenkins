/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.project

import java.nio.file.Path

// Paths variables bundled together to reduce parameter bloat on function calls
class project_paths implements Serializable
{
    String project_name
    String src_prefix = org.apache.commons.lang.RandomStringUtils.random(9, true, true)
    String project_src_prefix
    String build_prefix = src_prefix
    String project_build_prefix 
    String build_command = './install -c' 

    void construct_build_prefix()
    {
        project_build_prefix = build_prefix + '/' + project_name.toLowerCase();
    }
    
}
