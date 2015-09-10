package org.jenkinsci.plugins.ghprb.upstream;

import org.kohsuke.github.GHRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Kevin Suwala
 * This class is responsible for storing the GHRepository for each job and making
 * it accesible for other classes
 */

public class GhprbUpstreamStatusRepoPasser {
    private static Map<String, GHRepository> repoMap = new HashMap<String, GHRepository>();

    public static GHRepository addRepo(String name, GHRepository repo) {
        return repoMap.put(name, repo);
    }

    public static GHRepository removeRepo(String name) {
        return repoMap.remove(name);
    }
    
    public static GHRepository getRepo(String name) {
        return repoMap.get(name);
    }
}
