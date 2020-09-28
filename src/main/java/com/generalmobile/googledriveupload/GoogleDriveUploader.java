package com.generalmobile.googledriveupload;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import jnr.posix.util.Finder;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.oro.io.GlobFilenameFilter;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

@RequiresDomain(value = DriveScopeRequirement.class)
public final class GoogleDriveUploader extends Recorder implements SimpleBuildStep {

    public static final String APPLICATION_NAME = "Jenkins drive uploader";
    private final String credentialsId;
    private final String driveFolderName;
    private final String uploadFolder;
    private String sharedDriveName = "";
    private String userMail = "";
    private static HttpTransport httpTransport;

    static {
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    @DataBoundConstructor
    public GoogleDriveUploader(String credentialsId,  String driveFolderName, String uploadFolder, String userMail) {
        this.credentialsId = checkNotNull(credentialsId);
        this.driveFolderName = checkNotNull(driveFolderName);
        this.uploadFolder = checkNotNull(uploadFolder);
    }

    @DataBoundSetter
    public void setUserMail(String userMail) {
        this.userMail = checkNotNull(userMail);
    }
    
    @DataBoundSetter
    public void setSharedDriveName(String sharedDriveName) {
        this.sharedDriveName = checkNotNull(sharedDriveName);
    }

    public FormValidation doCheckUserMail(@QueryParameter String value) {
        return FormValidation.error("Not a number");
           /* int at =StringUtils.countOccurrencesOf(value,"@");
            int semicolon =StringUtils.countOccurrencesOf(value,";");

            if (at-1==semicolon)
                return FormValidation.warning("Please check mail again. If you using multi mail please separate each mail with ;");
            else
                return FormValidation.ok();*/

    }

    public String getCredentialsId() {
        return credentialsId;
    }
    
    public String getSharedDriveName() {
        return sharedDriveName;
    }
    
    public String getUploadFolder() {
        return uploadFolder;
    }

    public String getUserMail() {
        return userMail;
    }

    public String getDriveFolderName() {
        return driveFolderName;
    }
    
    @Override
    @SuppressFBWarnings
    public void perform(  @Nonnull  final Run<?, ?> run, @Nonnull final FilePath workspace,
        @Nonnull final  Launcher launcher, @Nonnull final TaskListener listener) throws InterruptedException, IOException {
 
        if ((run.getResult() == Result.FAILURE || run.getResult() == Result.ABORTED) ) {
            return;
        }
        try {
            listener.getLogger().println("Google Drive Uploading Plugin Started.");
            Set<Path> uploadPaths = getUploadFiles(Paths.get(workspace.toURI()) , uploadFolder, run.getEnvironment(listener));
            listener.getLogger().println("Uploading folder: " + workspace);
            if ( sharedDriveName.isEmpty()) {
                GoogleDriveManager driveManager = new GoogleDriveManager(getDriveService(), listener);
                for (Path uploadFilePath: uploadPaths) {
                    driveManager.uploadFolder(uploadFilePath.toFile(), getDriveFolderName(), userMail);
                }
            } else {
                SharedDriveManager driveManager = new SharedDriveManager(getDriveService(), sharedDriveName, listener);
                for (Path uploadFilePath: uploadPaths) {
                    driveManager.uploadFolderToSharedDrive(uploadFilePath.toFile(), getDriveFolderName());
                }
            }
        } catch (GeneralSecurityException e) {
            run.setResult(Result.FAILURE);
        }
    }

    static protected Set<Path> getUploadFiles(@Nonnull final Path rootPath, @Nonnull String uploadFolderPatterns, @Nonnull final EnvVars env) throws IOException {
        Set<Path> uploadFilePaths = new HashSet<Path>();
        if (!uploadFolderPatterns.isEmpty()){
            String[] uploadFilePatterns = env.expand(uploadFolderPatterns).split("\\s*,\\s*");
            for (String uploadFilePattern: uploadFilePatterns){
                PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + rootPath.toString() + File.separator + uploadFilePattern);
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (pathMatcher.matches(dir)) {
                            uploadFilePaths.add(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)  {
                        if (pathMatcher.matches(path)) {
                            uploadFilePaths.add(path);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } else {
            uploadFilePaths.add(rootPath);
        }
        return uploadFilePaths;
    }

    private Drive getDriveService() throws GeneralSecurityException {
        return new Drive.Builder(httpTransport, new JacksonFactory(), getAuthorizeCredentials())
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    private Credential getAuthorizeCredentials() throws GeneralSecurityException {
        return GoogleRobotCredentials
            .getById(getCredentialsId())
            .forRemote(getRequirement())
            .getGoogleCredential(getRequirement());
    }

    private DriveScopeRequirement getRequirement() {
        return DomainRequirementProvider.of(getClass(), DriveScopeRequirement.class);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    @Extension @Symbol("googleDriveUpload")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Google Drive Uploader";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
