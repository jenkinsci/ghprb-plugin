package org.jenkinsci.plugins.ghprb.manager.factory;

import com.cloudbees.plugins.flow.FlowRun;
import hudson.model.Run;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.impl.GhprbDefaultBuildManager;
import org.jenkinsci.plugins.ghprb.manager.impl.downstreambuilds.BuildFlowBuildManager;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public final class GhprbBuildManagerFactoryUtil {

    private GhprbBuildManagerFactoryUtil() {
    }

    /**
     * Gets an instance of a library that is able to calculate build urls depending of build type.
     * <p>
     * If the class representing the build type is not present on the classloader then default implementation is returned.
     *
     * @param build the job from Jenkins
     * @return a buildManager
     */
    public static GhprbBuildManager getBuildManager(Run<?, ?> build) {
        JobConfiguration jobConfiguration = JobConfiguration.builder().printStackTrace(false).build();

        return getBuildManager(build, jobConfiguration);
    }

    public static GhprbBuildManager getBuildManager(Run<?, ?> build, JobConfiguration jobConfiguration) {
        try {
            if (build instanceof FlowRun) {
                return new BuildFlowBuildManager(build, jobConfiguration);
            }
        } catch (NoClassDefFoundError ncdfe) {
        }

        return new GhprbDefaultBuildManager(build);
    }

}
