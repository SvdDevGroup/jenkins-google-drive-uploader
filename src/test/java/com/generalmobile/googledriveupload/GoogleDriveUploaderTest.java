/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import hudson.EnvVars;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class GoogleDriveUploaderTest {

    private EnvVars env = new EnvVars();
    private Path workspace;

    @Before
    public void setupLogger() throws IOException, URISyntaxException {
        workspace = Paths.get(this.getClass().getClassLoader().getResource("subdir").toURI()).getParent();
    }
    

    @Test
    public void getUploadFiles_withFileName() throws IOException, InterruptedException {
        Set<Path> uploadFiles = GoogleDriveUploader.getUploadFiles(workspace, "subdir/test_file_1.txt", env);
        assertEquals(1, uploadFiles.size());
    }

    @Test
    public void getUploadFiles_witDirName() throws IOException, InterruptedException {
        Set<Path> uploadFiles = GoogleDriveUploader.getUploadFiles(workspace, "subdir", env);
        assertEquals(1, uploadFiles.size());
    }
    
    @Test
    public void getUploadFiles_withDubbleStartPattern() throws IOException, InterruptedException {
        Set<Path> uploadFiles = GoogleDriveUploader.getUploadFiles(workspace, "**/*.txt", env);
        assertEquals(2, uploadFiles.size());
    }

    @Test
    public void getUploadFiles_withStarPattern() throws IOException, InterruptedException {
        Set<Path> uploadFiles = GoogleDriveUploader.getUploadFiles(workspace, "*/*1.txt", env);
        assertEquals(1, uploadFiles.size());
    }
    
    @Test
    public void getUploadFiles_witMultiplePatterns() throws IOException, InterruptedException {
        Set<Path> uploadFiles = GoogleDriveUploader.getUploadFiles(workspace, "subdir,*/*1.txt,**/*.txt", env);
        assertEquals(3, uploadFiles.size());
    }
}
