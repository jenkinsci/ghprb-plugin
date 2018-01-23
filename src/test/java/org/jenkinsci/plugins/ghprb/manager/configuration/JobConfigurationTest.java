package org.jenkinsci.plugins.ghprb.manager.configuration;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class JobConfigurationTest {

    @Test
    public void shouldNotPrintStackTrace() {
        JobConfiguration jobConfiguration = JobConfiguration.builder().printStackTrace(false).build();

        assertThat(jobConfiguration).isNotNull();
        assertThat(jobConfiguration.printStackTrace()).isFalse();
    }

    @Test
    public void shouldPrintStackTrace() {
        JobConfiguration jobConfiguration = JobConfiguration.builder().printStackTrace(true).build();

        assertThat(jobConfiguration).isNotNull();
        assertThat(jobConfiguration.printStackTrace()).isTrue();
    }

}
