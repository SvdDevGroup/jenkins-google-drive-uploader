/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import hudson.model.BuildListener;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.LogManager;

import static com.generalmobile.googledriveupload.DriveMockHttpTransport.BatchCreateRequest;
import static com.generalmobile.googledriveupload.DriveMockHttpTransport.FilesCreateRequest;
import static com.generalmobile.googledriveupload.DriveMockHttpTransport.FilesListRequest;
import static com.generalmobile.googledriveupload.DriveMockHttpTransport.FilesUploadRequest;
import static com.generalmobile.googledriveupload.GoogleDriveUploader.APPLICATION_NAME;
import static org.mockito.Mockito.mock;

public class GoogleDriveManagerTest {

    private static final transient Logger logger = LoggerFactory.getLogger(GoogleDriveManagerTest.class);

    public static final String FOLDER_ID = UUID.randomUUID().toString();
    public static final String FOLDER_NAME = "testFolder";
    public static final String SUB_FOLDER_ID = UUID.randomUUID().toString();
    public static final String FILE_ID = UUID.randomUUID().toString();
    public static final String FILE_TYPE = "txt";
    public static final String FILE_ID_2 = UUID.randomUUID().toString();
    public static final String PERMISSION_ID = UUID.randomUUID().toString();
    public static final String USER_EMAIL = "me@localtest.me";

    private DriveMockHttpTransport mockHttpTransport = new DriveMockHttpTransport();
    private MockBuildListenerHelper mockBuildListenerHelper = new MockBuildListenerHelper();
    private GoogleDriveManager googleDriveManager;

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            logger.info("Starting : {}", description.getMethodName());
            logger.info("==============================================");
        }
    };

    @BeforeClass
    public static void setupLogger() throws IOException {
        LogManager.getLogManager().readConfiguration(GoogleDriveManagerTest.class.getResourceAsStream("/logging.properties"));
    }

    @Before
    public void setupDriveService() {
        BuildListener mockBuildListener = mockBuildListenerHelper.createMockBuildListener();
        Credential mockCredential = mock(Credential.class);
        googleDriveManager = new GoogleDriveManager(
            new Drive.Builder(mockHttpTransport, new JacksonFactory(), mockCredential)
                .setApplicationName(APPLICATION_NAME)
                .build(),
            mockBuildListener);
    }

    @Test
    public void uploadFolder_withWithoutSourceFile() {
        // arrange
        mockHttpTransport.mock(FilesListRequest.emptyList());   // Search for driveFolder 
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME)); // Create driveFolder 
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, USER_EMAIL));  // Send out permissions driveFolder 
        // act
        googleDriveManager.uploadFolder(null, FOLDER_NAME, USER_EMAIL);
        // assert
        mockBuildListenerHelper.assertLoggingEqual(
            "userMail " + USER_EMAIL,
            "Searching for " + FOLDER_NAME,
            "Created new Folder " + FOLDER_NAME + " (" + FOLDER_ID + ") in root ()",
            "Write permissions set to Folder " + FOLDER_NAME + " for " + USER_EMAIL);
        mockBuildListenerHelper.assertNoErrors();  // TODO : should give error indication of invalid file
    }

    @Test
    public void uploadFolder_withExistingSourceFile() {
        // arrange
        File fileToUpload = new File(this.getClass().getClassLoader().getResource("subdir/test_file_1.txt").getFile());
        mockHttpTransport.mock(FilesListRequest.emptyList());  // Search for driveFolder 
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME)); // Create driveFolder 
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, USER_EMAIL)); // Send out permissions for driveFolder 
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for file in driveFolder
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE));  // create new Drive file 
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE)); // Upload file content
        // act
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, USER_EMAIL);
        // assert
        mockBuildListenerHelper.assertLoggingEqual(
            "userMail " + USER_EMAIL,
            "Searching for "  + FOLDER_NAME,
            "Created new Folder " + FOLDER_NAME + " (" + FOLDER_ID + ") in root ()",
            "Write permissions set to Folder " + FOLDER_NAME + " for " + USER_EMAIL,
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Creating new File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getAbsolutePath(),
            "Finished uploading " + fileToUpload.getAbsolutePath());
        mockBuildListenerHelper.assertNoErrors();
    }

    @Test
    public void uploadFolder_withNoneExistingSourceFile() {
        // arrange
        File subdir = new File(this.getClass().getClassLoader().getResource("subdir").getFile());
        File fileToUpload = new File(subdir, "invalid_file_name.txt");
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for driveFolder 
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME)); // Create driveFolder 
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, USER_EMAIL)); // Send out permissions driveFolder 
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for file in driveFolder
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE));  // create new Drive file  
        // act
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, USER_EMAIL);
        // assert
        mockBuildListenerHelper.assertLoggingEqual(
            "userMail " + USER_EMAIL,
            "Searching for "  + FOLDER_NAME,
            "Created new Folder " + FOLDER_NAME + " (" + FOLDER_ID + ") in root ()",
            "Write permissions set to Folder " + FOLDER_NAME + " for " + USER_EMAIL,
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Creating new File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getAbsolutePath());
        mockBuildListenerHelper.assertErrors(fileToUpload.getAbsolutePath() + " (No such file or directory)");
    }

    @Test
    public void uploadFolder_withExistingFileAndExistDestFolder() {
        // arrange
        File fileToUpload = new File(this.getClass().getClassLoader().getResource("subdir/test_file_1.txt").getFile());
        mockHttpTransport.mock(FilesListRequest.findFolder(FOLDER_ID, FOLDER_NAME));  // Search for driveFolder 
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for file in driveFolder
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE)); // create new Drive file 
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE)); // Upload file content
        // act
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, USER_EMAIL);
        // assert
        mockBuildListenerHelper.assertLoggingEqual(
            "userMail " + USER_EMAIL,
            "Searching for "  + FOLDER_NAME,
            "Found " + FOLDER_NAME  + " (" + FOLDER_ID + ")",
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Creating new File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getAbsolutePath(),
            "Finished uploading " + fileToUpload.getAbsolutePath());
        mockBuildListenerHelper.assertNoErrors();
    }

    @Test
    public void uploadFolder_withExistingFileAndExistDestFolderAndExistDestFile() {
        // arrange
        File fileToUpload = new File(this.getClass().getClassLoader().getResource("subdir/test_file_1.txt").getFile());
        mockHttpTransport.mock(FilesListRequest.findFolder(FOLDER_ID, FOLDER_NAME)); // Search for driveFolder 
        mockHttpTransport.mock(FilesListRequest.findFile(FILE_ID, fileToUpload.getName(), FILE_TYPE)); // Search for file in driveFolder 
        mockHttpTransport.mock(
            FilesUploadRequest.initiateUpdateUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE));  // Update existing  Drive file (new version) 
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, fileToUpload.getName(), FILE_TYPE));  // Upload file content
        // act
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, USER_EMAIL);
        // assert
        mockBuildListenerHelper.assertLoggingEqual(
            "userMail " + USER_EMAIL,
            "Searching for "  + FOLDER_NAME,
            "Found " + FOLDER_NAME+ " (" + FOLDER_ID + ")",
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Found " + fileToUpload.getName() + " (" + FILE_ID + ")",
            "Updating existing File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getAbsolutePath(),
            "Finished uploading " + fileToUpload.getAbsolutePath());
        mockBuildListenerHelper.assertNoErrors();
    }

    @Test
    public void uploadFolder_withExistingSourceDir() {
        // arrange
        File dirToUpload = new File(this.getClass().getClassLoader().getResource("subdir").getFile());
        File file_1 = new File(dirToUpload, "test_file_1.txt");
        File file_2 = new File(dirToUpload, "test_file_2.txt");
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for driveFolder 
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME));  // Create driveFolder 
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, USER_EMAIL)); // Send out permissions driveFolder 
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for subdir dir folder in driveFolder 
        mockHttpTransport.mock(FilesCreateRequest.createFolder(SUB_FOLDER_ID, dirToUpload.getName())); // Create subdir dir folder in driveFolder 
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for file in driveFolder
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, file_1.getName(), FILE_TYPE));  // create new Drive file 
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, file_1.getName(), FILE_TYPE)); // Upload file content
        mockHttpTransport.mock(FilesListRequest.emptyList()); // Search for 2nd file in driveFolder
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID_2, file_2.getName(), FILE_TYPE));  // create new Drive file 
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID_2, file_2.getName(), FILE_TYPE)); // Upload file content   
        // act
        googleDriveManager.uploadFolder(dirToUpload, FOLDER_NAME, USER_EMAIL);
        // assert
        mockBuildListenerHelper.assertLoggingEqual(
            "userMail " + USER_EMAIL,
            "Searching for "  + FOLDER_NAME,
            "Created new Folder " + FOLDER_NAME + " (" + FOLDER_ID + ") in root ()",
            "Write permissions set to Folder " + FOLDER_NAME + " for " + USER_EMAIL,
            "Searching for " + dirToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Created new Folder " + dirToUpload.getName() + " (" + SUB_FOLDER_ID + ") in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Searching for " + file_1.getName() + " in " + dirToUpload.getName() + " (" + SUB_FOLDER_ID + ")",
            "Creating new File " + file_1.getName() + " in " + dirToUpload.getName() + " (" + SUB_FOLDER_ID + ")",
            "Start uploading " + file_1.getAbsolutePath(),
            "Finished uploading " + file_1.getAbsolutePath(),
            "Searching for " + file_2.getName() + " in " + dirToUpload.getName() + " (" + SUB_FOLDER_ID + ")",
            "Creating new File " + file_2.getName() + " in " + dirToUpload.getName() + " (" + SUB_FOLDER_ID + ")",
            "Start uploading " + file_2.getAbsolutePath(),
            "Finished uploading " + file_2.getAbsolutePath());
        mockBuildListenerHelper.assertNoErrors();
    }
}

