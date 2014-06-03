/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.copyartifact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;

/**
 *　Job Property to define projects that can copy artifacts of this project.
 */
public class CopyArtifactPermissionProperty extends JobProperty<AbstractProject<?,?>> {
    public static final String PROPERTY_NAME = "copy-artifact-permission-property";
    
    private final List<String> projectNameList;
    
    /**
     * @return list of project names that can copy artifacts of this project.
     */
    public List<String> getProjectNameList() {
        return projectNameList;
    }
    
    /**
     * @return comma-separated project names that can copy artifacts of this project.
     */
    public String getProjectNames() {
        return StringUtils.join(projectNameList, ',');
    }
    
    /**
     * Constructor
     * 
     * @param projectNames comma-separated project names that can copy artifacts of this project.
     */
    @DataBoundConstructor
    public CopyArtifactPermissionProperty(String projectNames) {
        List<String> rawProjectNameList = Arrays.asList((projectNames != null)?StringUtils.split(projectNames, ','):new String[0]);
        projectNameList = new ArrayList<String>(rawProjectNameList.size());
        for (String rawProjectName: rawProjectNameList) {
            if (StringUtils.isBlank(rawProjectName)) {
                continue;
            }
            projectNameList.add(StringUtils.trim(rawProjectName));
        }
    }
    
    /**
     * @param copier a project who wants to copy artifacts of this project.
     * @return whether copier is allowed to copy artifacts of this project.
     */
    public boolean canCopiedBy(AbstractProject<?,?> copier) {
        String copierName = copier.getRelativeNameFrom(owner.getParent());
        String absoluteName = String.format("/%s", copier.getFullName()); 
            // Note: getFullName() returns not an absolute path, but a relative path from root...
        for (String projectName: getProjectNameList()) {
            if (isNameMatch(copierName, projectName) || isNameMatch(absoluteName, projectName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * package scope for testing purpose.
     * 
     * @param name
     * @param pattern
     * @return whether name matches pattern.
     */
    /*package*/ static boolean isNameMatch(String name, String pattern) {
        if (pattern == null || name == null) {
            return false;
        }
        if (!pattern.contains("*")) {
            // if no wild card, simply complete match.
            return pattern.equals(name);
        }
        
        List<String> literals = Arrays.asList(pattern.split("\\*", -1));
        String regex = StringUtils.join(Lists.transform(literals, new Function<String, String>() {
            public String apply(String input) {
                return Pattern.quote(input);
            }
        }), ".*");
        return name.matches(regex);
    }
    
    /**
     * Convenient wrapper for {@link CopyArtifactPermissionProperty#canCopiedBy(AbstractProject)}
     * 
     * @param copier a project that wants to copy artifacts of copiee.
     * @param copiee a owner of artifacts.
     * @return whether copier can copy artifacts of copiee.
     */
    public static boolean canCopyArtifact(AbstractProject<?,?> copier, AbstractProject<?,?> copiee) {
        CopyArtifactPermissionProperty prop = copiee.getProperty(CopyArtifactPermissionProperty.class);
        if (prop == null) {
            return false;
        }
        return prop.canCopiedBy(copier);
    }
    
    /**
     * Descriptor for {@link CopyArtifactPermissionProperty}.
     */
    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        /**
         * @return name displayed in the project configuration page.
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.CopyArtifactPermissionProperty_DisplayName();
        }
        
        /**
         * @return key name used in the configuration form.
         */
        public String getPropertyName() {
            return PROPERTY_NAME;
        }
        
        /**
         * @param req
         * @param formData
         * @return
         * @throws hudson.model.Descriptor.FormException
         * @see hudson.model.JobPropertyDescriptor#newInstance(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
         */
        @Override
        public CopyArtifactPermissionProperty newInstance(StaplerRequest req, JSONObject formData)
                throws hudson.model.Descriptor.FormException {
            if(formData == null || formData.isNullObject()) {
                return null;
            }
            JSONObject form = formData.getJSONObject(getPropertyName());
            if(form == null || form.isNullObject()) {
                return null;
            }
            
            return (CopyArtifactPermissionProperty)super.newInstance(req, form);
        }
        
        /**
         * package scope for testing purpose.
         * 
         * @param projectNames
         * @param context
         * @return
         */
        /*package*/ List<String> checkNotFoundProjects(String projectNames, ItemGroup<?> context) {
            if (StringUtils.isBlank(projectNames)) {
                return Collections.emptyList();
            }
            List<String> notFound = new ArrayList<String>();
            for (String projectName: StringUtils.split(projectNames, ',')) {
                if (StringUtils.isBlank(projectName)) {
                    continue;
                }
                projectName = StringUtils.trim(projectName);
                if (projectName.contains("*")) {
                    // no check for pattern
                    continue;
                }
                AbstractProject<?,?> proj = Jenkins.getInstance().getItem(projectName, context, AbstractProject.class);
                if (proj == null || proj.getRootProject() != proj || !proj.hasPermission(Item.READ)) {
                    // permission check is done only for root project.
                    notFound.add(projectName);
                    continue;
                }
            }
            return notFound;
        }
        
        /**
         * @param projectNames
         * @return
         */
        public FormValidation doCheckProjectNames(@QueryParameter String projectNames, @AncestorInPath ItemGroup<?> context) {
            List<String> notFound = checkNotFoundProjects(projectNames, context);
            if (!notFound.isEmpty()) {
                return FormValidation.warning(Messages.CopyArtifactPermissionProperty_MissingProject(StringUtils.join(notFound, ",")));
            }
            return FormValidation.ok();
        }
        
        /**
         * @param value
         * @param context
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteProjectNames(@QueryParameter String value, @AncestorInPath ItemGroup<?> context) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            if (StringUtils.isBlank(value)) {
                return candidates;
            }
            value = StringUtils.trim(value);
            for (AbstractProject<?,?> project: Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                if (project.getRootProject() != project) {
                    // permission check is done only for root project.
                    continue;
                }
                if (!project.hasPermission(Item.READ)) {
                    continue;
                }
                
                String relativeName = project.getRelativeNameFrom(context);
                if (relativeName.startsWith(value)) {
                    candidates.add(relativeName);
                }
            }
            return candidates;
        }
    }
}
