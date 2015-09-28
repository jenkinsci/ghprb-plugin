package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Excludes {@link GhprbRootAction} from the CSRF protection.
 * @since 1.28
 */
@Extension
public class GhprbCrumbExclusion extends CrumbExclusion {

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        final String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith(getExclusionPath())) {
            chain.doFilter(req, resp);
            return true;
        }
        return false;
    }

    public String getExclusionPath() {
        return "/" + GhprbRootAction.URL + "/";
    }
}