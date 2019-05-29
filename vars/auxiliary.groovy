/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */
 
////////////////////////////////////////////////////////////////////////
// -- AUXILLARY HELPER FUNCTIONS
import java.nio.file.Path;
import com.amd.project.project_paths;
import com.amd.project.compiler_data;

////////////////////////////////////////////////////////////////////////
// Check whether job was started by a timer
@NonCPS
def isJobStartedByTimer() {
    def startedByTimer = false
    try {
	def buildCauses = currentBuild.rawBuild.getCauses()
	for ( buildCause in buildCauses ) {
	    if (buildCause != null) {
		def causeDescription = buildCause.getShortDescription()
		echo "shortDescription: ${causeDescription}"
		if (causeDescription.contains("Started by timer")) {
		    startedByTimer = true
		}
	    }
	}
    } catch(theError) {
	echo "Error getting build cause"
    }

    return startedByTimer
}

////////////////////////////////////////////////////////////////////////
// Return build number of upstream job
@NonCPS
int get_upstream_build_num( )
{
    def upstream_cause = currentBuild.rawBuild.getCause( hudson.model.Cause$UpstreamCause )
    if( upstream_cause == null)
    return 0

    return upstream_cause.getUpstreamBuild()
}

////////////////////////////////////////////////////////////////////////
// Return project name of upstream job
@NonCPS
String get_upstream_build_project( )
{
    def upstream_cause = currentBuild.rawBuild.getCause( hudson.model.Cause$UpstreamCause )
    if( upstream_cause == null)
    return null

    return upstream_cause.getUpstreamProject()
}

////////////////////////////////////////////////////////////////////////
// Calculate the relative path between two sub-directories from a common root
@NonCPS
String g_relativize( String root_string, String rel_source, String rel_build )
{
    Path root_path = new File( root_string ).toPath( )
    Path path_src = root_path.resolve( rel_source )
    Path path_build = root_path.resolve( rel_build )

    return path_build.relativize( path_src ).toString( )
}

////////////////////////////////////////////////////////////////////////
// Construct the relative path of the build directory
void build_directory_rel( project_paths paths, compiler_data hcc_args )
{
    //   if( hcc_args.build_config.equalsIgnoreCase( 'release' ) )
    //   {
    //     paths.project_build_prefix = paths.build_prefix + '/' + paths.project_name + '/release';
    //   }
    //   else
    //   {
    //     paths.project_build_prefix = paths.build_prefix + '/' + paths.project_name + '/debug';
    //   }
    paths.project_build_prefix = paths.build_prefix + '/' + paths.project_name;
}
