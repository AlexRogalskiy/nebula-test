package nebula.test.functional.internal.toolingapi

import nebula.test.functional.internal.GradleHandle
import nebula.test.functional.internal.GradleHandleBuildListener
import nebula.test.functional.internal.GradleHandleFactory
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.wrapper.WrapperExecutor

public class ToolingApiGradleHandleFactory implements GradleHandleFactory {
    static final String FORK_SYS_PROP = 'nebula.test.functional.fork'
    private final boolean fork
    private final String version

    ToolingApiGradleHandleFactory(boolean fork, String version) {
        this.fork = fork
        this.version = version
    }

    public GradleHandle start(File projectDir, List<String> arguments) {
        GradleConnector connector = createGradleConnector(projectDir)

        boolean forkedProcess = isForkedProcess()

        // Allow for in-process debugging
        connector.embedded(!forkedProcess)

        ProjectConnection connection = connector.connect();
        BuildLauncher launcher = createBuildLauncher(connection, arguments)
        createGradleHandle(connection, launcher, forkedProcess)
    }

    private GradleConnector createGradleConnector(File projectDir) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectDir);
        configureGradleVersion(connector, projectDir)
        connector
    }

    private void configureGradleVersion(GradleConnector connector, File projectDir) {
        if (version != null) {
            connector.useGradleVersion(version)
        } else {
            configureWrapperDistributionIfUsed(connector, projectDir)
        }
    }

    private void configureWrapperDistributionIfUsed(GradleConnector connector, File projectDir) {
        BuildLayout layout = new BuildLayoutFactory().getLayoutFor(projectDir, true)
        WrapperExecutor wrapper = WrapperExecutor.forProjectDirectory(layout.rootDirectory, System.out)
        if (wrapper.distribution) {
            // This would be in the test project
            connector.useDistribution(wrapper.distribution)
        } else {
            // Search above us, in the project that owns the test
            BuildLayout layoutParent = new BuildLayoutFactory().getLayoutFor(projectDir.parentFile, true)
            WrapperExecutor wrapperParent = WrapperExecutor.forProjectDirectory(layoutParent.rootDirectory, System.out)
            if (wrapperParent.distribution) {
                connector.useDistribution(wrapperParent.distribution)
            }
        }
    }

    private boolean isForkedProcess() {
        if (fork) {
            return true
        }

        Boolean.parseBoolean(System.getProperty(FORK_SYS_PROP, Boolean.FALSE.toString()))
    }

    private BuildLauncher createBuildLauncher(ProjectConnection connection, List<String> arguments) {
        BuildLauncher launcher = connection.newBuild();
        String[] argumentArray = new String[arguments.size()];
        arguments.toArray(argumentArray);
        launcher.withArguments(argumentArray);
        launcher
    }

    private GradleHandle createGradleHandle(ProjectConnection connection, BuildLauncher launcher, boolean forkedProcess) {
        GradleHandleBuildListener toolingApiBuildListener = new ToolingApiBuildListener(connection)
        BuildLauncherBackedGradleHandle buildLauncherBackedGradleHandle = new BuildLauncherBackedGradleHandle(launcher, forkedProcess)
        buildLauncherBackedGradleHandle.registerBuildListener(toolingApiBuildListener)
        buildLauncherBackedGradleHandle
    }

    private class ToolingApiBuildListener implements GradleHandleBuildListener {
        private final ProjectConnection connection

        ToolingApiBuildListener(ProjectConnection connection) {
            assert connection != null, 'Requires a non-null connection'
            this.connection = connection
        }

        @Override
        void buildStarted() {}

        @Override
        void buildFinished() {
            connection.close()
        }
    }
}
