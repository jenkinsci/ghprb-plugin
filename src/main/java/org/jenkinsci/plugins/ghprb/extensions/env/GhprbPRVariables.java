package org.jenkinsci.plugins.ghprb.extensions.env;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.github.GHPullRequest;

public class GhprbPRVariables {
    
    public Map<String, String> getEnvList() {
        Map<String, String> variables = new HashMap<String, String>(10);
        Method[] methods = GHPullRequest.class.getMethods();
        for (Method method: methods) {
            String name = method.getName();
            if (name.startsWith("get") || name.startsWith("is")) {
                String fixed = name.replaceAll("get(.)", "$1");
                variables.put(name, name);
            }
        }
        
        return variables;
    }

}
