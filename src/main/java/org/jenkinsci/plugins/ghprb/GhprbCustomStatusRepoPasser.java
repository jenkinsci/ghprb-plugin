package org.jenkinsci.plugins.ghprb;

import org.kohsuke.github.GHRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Kevin Suwala
 * This class is responsible for storing the GHRepository for each job and making
 * it accesible for other classes
 */

public class GhprbCustomStatusRepoPasser {
    private static Map<String, GHRepository> repoMap = new HashMap<String, GHRepository>();

    public static void addRepo(String name, GHRepository repo) {
        repoMap.put(name, repo);
    }

    public static Map<String, GHRepository> getRepoMap() {
        return repoMap;
    }
}
