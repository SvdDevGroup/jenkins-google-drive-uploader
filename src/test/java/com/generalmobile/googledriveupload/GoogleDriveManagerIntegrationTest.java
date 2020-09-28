/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.model.BuildListener;
import junit.GoogleDriveIntegrationTest;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.LogManager;

import static com.generalmobile.googledriveupload.GoogleDriveUploader.APPLICATION_NAME;
import static com.generalmobile.googledriveupload.ManagerBase.GOOGLE_DRIVE_FOLDER_MIMETYPE;

@Category(GoogleDriveIntegrationTest.class)
@Ignore("Not for automatic test, only run manually after configuring integration_test.properties ")
public class GoogleDriveManagerIntegrationTest {
    private static final transient Logger logger = LoggerFactory.getLogger(GoogleDriveManagerIntegrationTest.class);

    private MockBuildListenerHelper mockBuildListenerHelper = new MockBuildListenerHelper();
    private GoogleDriveManager googleDriveManager;
    private static String googleJsonFilePath;
    private static String googleProjectId;
    private static String driveFolderName;
    private static String emailUser;
    private static Credential credential;

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    @BeforeClass
    public static void setupLogger() throws Exception {
        LogManager.getLogManager().readConfiguration(GoogleDriveManagerTest.class.getResourceAsStream("/logging.properties"));
        Properties integrationTestProperties = new Properties();
        // Copy integration_test.template.properties to integration_test.properties and update properties
        integrationTestProperties.load(GoogleDriveManagerIntegrationTest.class.getClassLoader().getResourceAsStream("integration_test.properties"));
        googleJsonFilePath = integrationTestProperties.getProperty("googleJsonFilePath");
        googleProjectId = integrationTestProperties.getProperty("googleProjectId");
        driveFolderName = integrationTestProperties.getProperty("driveFolderName");
        emailUser = integrationTestProperties.getProperty("emailUser");
        credential = createCredentaionFromGooglJsonFile();
    }

    @Before
    public void setupDriveService() throws Exception {
        BuildListener mockBuildListener = mockBuildListenerHelper.createMockBuildListener();
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        googleDriveManager = new GoogleDriveManager(
            new Drive.Builder(httpTransport, new JacksonFactory(), credential)
                .setApplicationName(APPLICATION_NAME)
                .build(),
            mockBuildListener);
    }
    
    @After
    public void cleanupDrive() {
        googleDriveManager.cleanup(GOOGLE_DRIVE_FOLDER_MIMETYPE, Arrays.asList("subdir", "subfolder", driveFolderName));
        googleDriveManager.cleanup("text/plain", Arrays.asList("test_file_1.txt", "test_file_2.txt"));
    }
    
    static private Credential createCredentaionFromGooglJsonFile() throws Exception {
        File jsonFile = new File(googleJsonFilePath);
        DiskFileItemFactory factory = new DiskFileItemFactory();
        FileItem dfi = factory.createItem("", "application/octet-stream", false, jsonFile.getName());
        Files.copy(jsonFile.toPath(), dfi.getOutputStream());
        JsonServiceAccountConfig jsonConfig = new JsonServiceAccountConfig(dfi, googleJsonFilePath);
        jsonConfig.setJsonKeyFileUpload(dfi);
        GoogleRobotCredentials creds = new GoogleRobotPrivateKeyCredentials(googleProjectId, jsonConfig, null);
        GoogleOAuth2ScopeRequirement requirement = new DriveScopeRequirement();
        return creds.forRemote(requirement).getGoogleCredential(requirement);
    }
    
    @Test
    public void uploadFolder_withSourceSingleFile() {
        File fileToUpload = new File(this.getClass().getClassLoader().getResource("subdir/test_file_1.txt").getFile());
        googleDriveManager.uploadFolder(fileToUpload, driveFolderName, emailUser);
    }

    @Test
    public void uploadFolder_withSourceDir() {
        File fileToUpload = new File(this.getClass().getClassLoader().getResource("subdir").getFile());
        googleDriveManager.uploadFolder(fileToUpload, driveFolderName, emailUser);
    }

    @Test
    public void uploadFolder_withMultiLevelDriveFolder() throws GeneralSecurityException {
        File fileToUpload = new File(this.getClass().getClassLoader().getResource("subdir/test_file_1.txt").getFile());
        googleDriveManager.uploadFolder(fileToUpload , driveFolderName + "/subfolder", emailUser);
    }
}
