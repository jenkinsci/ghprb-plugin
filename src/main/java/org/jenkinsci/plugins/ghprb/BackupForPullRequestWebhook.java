package org.jenkinsci.plugins.ghprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.AsyncAperiodicWork;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * In case of unreachable CI due to n/w issue or CI corruption, web hook payloads will
 * not reach CI and build won't trigger for new PR(s). Below class serves as a safe
 * guard mechanism to make sure CI jobs (GitHub PR web hook trigger enabled) don’t miss any
 * PR by polling for new PR(s) on certain conditions.
 */
@Extension
public class BackupForPullRequestWebhook extends AsyncAperiodicWork {

    private static final Logger LOGGER = getLogger(BackupForPullRequestWebhook.class);

    private static BackupForPullRequestWebhook currentTask;
    private static final int LAST_BUILD_TIME_THRESHOLD = 45 * 60 * 1000;
    private static final int RECURRENCE_PERIOD = 60 * 60 * 1000;

    public BackupForPullRequestWebhook() {
        super("Check for new pull requests");
    }

    @Override
    protected void execute(TaskListener taskListener) throws IOException, InterruptedException {
        LOGGER.info("Executing BackupForPullRequestWebhook");
        if (isBackupForPRWebhookEnabled()) {
            List<AbstractProject> jobs = Jenkins.getInstance().getItems(AbstractProject.class);
            for (AbstractProject job : jobs) {
                if (isEligible(job)) {
                    GhprbTrigger ghprbTrigger = (GhprbTrigger) job.getTrigger(GhprbTrigger.class);
                    ghprbTrigger.getRepository().check();
                }
            }
        }
    }

    private boolean isEligible(AbstractProject job) {
        Trigger ghprbTrigger = job.getTrigger(GhprbTrigger.class);
        if (ghprbTrigger != null && ((GhprbTrigger)ghprbTrigger).getUseGitHubHooks()) {
            if (!job.isBuilding() && !job.isInQueue() && job.isBuildable()) {
                if (job.getLastBuild() != null) {
                    if (System.currentTimeMillis() - job.getLastBuild().getTimeInMillis() > getLastBuildTimeThreshold()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private long getLastBuildTimeThreshold() {
        String key = BackupForPullRequestWebhook.class.getName() + ".lastBuildTimeThreshold";
        String value = getFromGlobalProperties(key);
        if (StringUtils.isBlank(value)) {
            return Long.getLong(key, LAST_BUILD_TIME_THRESHOLD);
        }
        return Long.parseLong(value);
    }

    private boolean isBackupForPRWebhookEnabled() {
        String key = BackupForPullRequestWebhook.class.getName() + ".enabled";
        String value = getFromGlobalProperties(key);
        if (StringUtils.isBlank(value)) {
            return Boolean.getBoolean(key);
        }
        return Boolean.parseBoolean(value);
    }

    private String getFromGlobalProperties(String key) {
        EnvVars vars = new EnvVars();
        DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalProps =
                Jenkins.getInstance().getGlobalNodeProperties();
        List<EnvironmentVariablesNodeProperty> properties =
                globalProps.getAll(EnvironmentVariablesNodeProperty.class);
        for (EnvironmentVariablesNodeProperty environmentVariablesNodeProperty : properties) {
            vars.putAll(environmentVariablesNodeProperty.getEnvVars());
        }
        return vars.get(key);
    }

    @Override
    public long getRecurrencePeriod() {
        String key = BackupForPullRequestWebhook.class.getName() + ".recurrencePeriod";
        String value = getFromGlobalProperties(key);
        if (StringUtils.isBlank(value)) {
            return Long.getLong(key, RECURRENCE_PERIOD);
        }
        return Long.parseLong(value);
    }

    @Override
    public AperiodicWork getNewInstance() {
        if (currentTask!=null) {
            currentTask.cancel();
        }
        else {
            cancel();
        }
        currentTask = new BackupForPullRequestWebhook();
        return currentTask;
    }
}
