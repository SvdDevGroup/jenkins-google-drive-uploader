/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.File;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.TaskListener;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.Optional;

public abstract class ManagerBase {
    public static final String GOOGLE_DRIVE_FOLDER_MIMETYPE = "application/vnd.google-apps.folder";
    private static final int MB = 0x100000;
    
    protected final Drive drive;
    protected final TaskListener listener;

    protected abstract Optional<File> findInFolderByQuery(final String query);
    protected abstract File createNewFolder(final File destFolder, final String name);
    protected abstract DriveRequest<File> createNewFileRequest(final File destFolder, final java.io.File source) throws IOException;
    protected abstract DriveRequest<File> createUpdateFileRequest(final File existingFile, final File destFolder, final java.io.File source) throws IOException;
    
    ManagerBase(final Drive driveService, final TaskListener listener) {
        this.drive = driveService;
        this.listener = listener;
    }
    
    @SuppressFBWarnings
    protected void uploadFile(final java.io.File source, File destFolder) {
        try {
            if (source != null && source.isDirectory()) {
                File destSubFolder = findFolderInFolder(destFolder, source.getName())
                    .orElseGet(() -> createNewFolder(destFolder, source.getName()));
                if (destSubFolder != null) {
                    for (java.io.File sourceFile : Objects.requireNonNull(source.listFiles())) {
                        uploadFile(sourceFile, destSubFolder);
                    }
                }
            } else {
                if (source != null) {
                    DriveRequest<File> request;
                    Optional<File> existingFile = findInFolder(destFolder, source.getName());
                    if (existingFile.isPresent()) {
                        listener.getLogger().printf("Updating existing File %s in %s (%s)%n", source.getName(), destFolder.getName(), destFolder.getId());
                        request = createUpdateFileRequest(existingFile.get(), destFolder, source);
                    } else {
                        listener.getLogger().printf("Creating new File %s in %s (%s)%n", source.getName(), destFolder.getName(), destFolder.getId());
                        request =  createNewFileRequest(destFolder, source);
                    }
                    executeDriveRequest(request, source.getPath());
                }
            }
        } catch (IOException e) {
            listener.error(e.getMessage());
        }
    }

    protected Optional<File> findFolderInFolder(File parentFolder, String name) {
        listener.getLogger().printf("Searching for %s in %s (%s)%n", name, parentFolder.getName(), parentFolder.getId());
        return findInFolderByQuery(String.format("mimeType='%s' and name='%s' and '%s' in parents and trashed=false",
            GOOGLE_DRIVE_FOLDER_MIMETYPE, name, parentFolder.getId()));
    }

    protected Optional<File> findInFolder(File parentFolder, String name) {
        listener.getLogger().printf("Searching for %s in %s (%s)%n", name, parentFolder.getName(), parentFolder.getId());
        return findInFolderByQuery(String.format("name='%s' and '%s' in parents and trashed=false",
            name, parentFolder.getId()));
    }
    
    private void executeDriveRequest(final DriveRequest<File> request, final String filePath) throws IOException {
        MediaHttpUploader httpUploader = request.getMediaHttpUploader();
        httpUploader.setDirectUploadEnabled(false);
        httpUploader.setChunkSize(2 * MB);
        httpUploader.setProgressListener(uploader -> {
            switch (uploader.getUploadState()) {
            case INITIATION_STARTED:
                listener.getLogger().printf("Start uploading %s%n", filePath);
                break;
            case MEDIA_IN_PROGRESS:
                NumberFormat formatter = new DecimalFormat("#0.00");
                String progress = formatter.format(uploader.getProgress() * 100);
                listener.getLogger().println("Uploading in progress %" + progress);
                break;
            case MEDIA_COMPLETE:
                listener.getLogger().printf("Finished uploading %s%n", filePath);
                break;
            default:
                // Ignore other states
                break;
            }
        });
        request.execute();
    }
}
