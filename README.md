# rocJENKINS
Shared Libraries for rocm Jenkins continuous integration intended for internal AMD use.

## Style Guide
Groovy is a derivative of Java. Please follow the Style Guides Below:

http://groovy-lang.org/style-guide.html
https://www.oracle.com/technetwork/java/codeconventions-150003.pdf

## CentOS Support
In the project's Jenkinsfile, do the following to enable CentOS support:

1) Define nodes by changing the label from the GPU architecture to include CentOS 7.
   
        Ubuntu (Default): def nodes = new dockerNodes(['gfx900'], myproj)
        CentOS: def nodes = new dockerNodes(['gfx900 && centos7'], myproj)
    
->  Please note this is case sensitive- only centos7 is supported in rocJenkins, so be sure to include 'centos7' when defining the node.

->  Try and add 1 Ubuntu instance and 1 CentOS instance, on different GPU architecture.
    
        def nodes = new dockerNodes(['gfx900', 'gfx906 && centos7'], myproj)

2) Packaging in centOS creates an rpm file instead of a debian (.deb) file. Redefine your packaging command to enable packaging for rpm files. 
   See the example below for the new packaging command, assuming both CentOS and Ubuntu nodes are present:
     
        def packageCommand =
        {
            platform, project->

            def command 
        
            if(platform.jenkinsLabel.contains('centos'))
            {
                command = """
                        set -x
                        cd ${project.paths.project_build_prefix}/build/release
                        make package
                        rm -rf package && mkdir -p package
                        mv *.rpm package/
                        rpm -qlp package/*.rpm
                        """

                platform.runCommand(this, command)
                platform.archiveArtifacts(this, """${project.paths.project_build_prefix}/build/release/package/*.rpm""")        
            }
            else
            {
                command = """
                        set -x
                        cd ${project.paths.project_build_prefix}/build/release
                        make package
                        rm -rf package && mkdir -p package
                        mv *.deb package/
                        dpkg -c package/*.deb
                        """

                platform.runCommand(this, command)
                platform.archiveArtifacts(this, """${project.paths.project_build_prefix}/build/release/package/*.deb""")
            }
        }