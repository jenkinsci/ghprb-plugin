package org.jenkinsci.plugins.ghprb.manager.configuration;

/**
 * @author Miguel Pastor
 */
public class JobConfiguration {

	public static PrintStackTrace builder() {
		return new JobConfigurationBuilder();
	}

	private boolean printStacktrace;

	private JobConfiguration() {
	}

	public boolean printStackTrace() {
		return this.printStacktrace;
	}

	public static interface PrintStackTrace {
		Build printStackTrace(boolean print);
	}

	public static interface Build {
		JobConfiguration build();
	}


	public static final class JobConfigurationBuilder implements Build, PrintStackTrace {

		private JobConfiguration jobConfiguration = new JobConfiguration();

		public JobConfiguration build() {
			return jobConfiguration;
		}

		public Build printStackTrace(boolean print) {
			jobConfiguration.printStacktrace = print;
			return this;
		}
	}

}


