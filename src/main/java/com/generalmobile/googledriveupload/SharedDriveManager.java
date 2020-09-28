/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.TeamDrive;
import com.google.api.services.drive.model.TeamDriveList;
import hudson.model.TaskListener;

import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SharedDriveManager extends ManagerBase {
    protected TeamDrive teamDrive;

    SharedDriveManager(final Drive driveService, final String sharedDriveName, final TaskListener listener) throws GeneralSecurityException {
        super(driveService, listener);
        teamDrive = findSharedDrive(sharedDriveName).
            orElseThrow(() -> new GeneralSecurityException("Could not find the shared drive " + sharedDriveName));
    }

    void uploadFolderToSharedDrive(java.io.File source, String destFolderName) throws GeneralSecurityException {
        File destFolder = findDestFolderInSharedDrive(destFolderName);
        if (destFolder == null ) {
            throw new GeneralSecurityException("Could not create " + destFolderName);
        }
        listener.getLogger().printf("Destintation Folder %s (%s)%n", destFolder.getName(), destFolder.getId());
        uploadFile(source, destFolder);
    }

    @Override
    protected Optional<File> findInFolderByQuery(final String query){
        try {
            String pageToken = null;
            do {
                FileList result = drive.files().list()
                    .setQ(query)
                    .setPageToken(pageToken)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setTeamDriveId(teamDrive.getId())
                    .setCorpora("drive")
                    .execute();
                for (File file : result.getFiles()) {
                    listener.getLogger().printf("Found %s (%s)%n", file.getName(), file.getId());
                    return Optional.of(file);
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        } catch (IOException e) {
            listener.error(e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    protected  File createNewFolder(final File parentFolder, final String name){
        return createNewFolder(parentFolder.getName(), parentFolder.getId(), name);
    }
    
    @Override
    protected DriveRequest<File> createUpdateFileRequest(final File existingFile, final File destFolder, final java.io.File source) throws IOException {
        // Create new File content
        String type = Files.probeContentType(source.toPath());
        FileContent newContentInputStream = new FileContent(type, source);
        // For update API file should not have any parents, but should use addParents (=> using empty collection) 
        File newContent = createNewFile(Collections.emptyList(), source.getName(), type); 
        // Update existing file with the new File content
        return drive.files()
            .update(existingFile.getId(), newContent, newContentInputStream)
            .setSupportsTeamDrives(true)
            .setAddParents(destFolder.getId()); 
    }

    @Override
    protected DriveRequest<File> createNewFileRequest(final File destFolder, final java.io.File source) throws IOException {
        // Create new File content
        String type = Files.probeContentType(source.toPath());
        FileContent newContentInputStream = new FileContent(type, source);
        File newContent = createNewFile(Collections.singletonList(destFolder.getId()), source.getName(), type);
        // Create a new file with the new File content
        return drive.files()
            .create(newContent, newContentInputStream)
            .setSupportsTeamDrives(true);
    }
    
    private Optional<TeamDrive> findSharedDrive(String sharedDriveName) {
        try {
            listener.getLogger().println("Searching for TeamDrives");
            TeamDriveList teamDriveList = drive.teamdrives().list().execute();
            if (teamDriveList.getTeamDrives() != null && !teamDriveList.getTeamDrives().isEmpty()) {
                for (TeamDrive teamDrive : teamDriveList.getTeamDrives()) {
                    if (teamDrive.getName().equals(sharedDriveName)) {
                        return Optional.of(teamDrive);
                    }
                }
            }
        } catch (IOException e) {
            listener.error("Error accessing TeamDriveList ", e);
        }
        listener.error("Shared Drive " + sharedDriveName + " not found.");
        return Optional.empty();
    }

   private File findDestFolderInSharedDrive(final String destFolderName) throws GeneralSecurityException {
        String[] destinationFolders = destFolderName.split("/");
        // Find or create to level folder
        listener.getLogger().printf("Searching for %s in %s (%s)%n", destinationFolders[0], teamDrive.getName(), teamDrive.getId());
        File destFolder = findInFolderByQuery(String.format("mimeType='%s' and name='%s' and trashed=false",
            GOOGLE_DRIVE_FOLDER_MIMETYPE, destinationFolders[0]))
            .orElseGet(() -> createNewFolder(teamDrive.getName(), teamDrive.getId(), destinationFolders[0]));
        // Find or create additional subdirs
        for(int i=1; i< destinationFolders.length && destFolder != null ; i++) {
            String subFolderName = destinationFolders[i]; 
            File newParentFolder = destFolder;
            destFolder = findFolderInFolder(newParentFolder, subFolderName)
                .orElseGet(() -> createNewFolder(newParentFolder.getName(), newParentFolder.getId(), subFolderName));
        }
        return destFolder;
    }
    
    private  File createNewFolder(final String parentName, String parentId, final String name){
        // Need to create the folder...
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setTeamDriveId(teamDrive.getId());
        fileMetadata.set("supportsTeamDrives", true);
        fileMetadata.setMimeType(GOOGLE_DRIVE_FOLDER_MIMETYPE);
        fileMetadata.setParents(Collections.singletonList(parentId));
        try {
            File newFolder = drive.files().create(fileMetadata)
                .setSupportsTeamDrives(true)
                .setFields("id, name, parents")
                .execute();
            listener.getLogger().printf("Created new Folder %s (%s) in %s (%s)%n",
                newFolder.getName(), newFolder.getId(), parentName, parentId);
            return newFolder;
        } catch (IOException e) {
            listener.error("Error creating folder in Shared Drive", e);
        }
        return null;
    }
    
    private File createNewFile(final List<String> parentIds,final String name, final String type){
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setTeamDriveId(teamDrive.getId());
        fileMetadata.set("supportsTeamDrives", true);
        fileMetadata.setMimeType(type);
        fileMetadata.setParents(parentIds);
        return fileMetadata;
    }

    public void cleanup(final String type, final List<String> names) {
        try {
            String pageToken = null;
            do {
                FileList result = drive.files().list()
                    .setPageToken(pageToken)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setTeamDriveId(teamDrive.getId())
                    .setCorpora("drive")
                    .execute();
                List<File> files = result.getFiles();
                for (File file : files) {
                    if (file.getMimeType().equals(type) && names.contains(file.getName()) ) {
                        deleteFile(file);
                    }
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        } catch (IOException e) {
            listener.error("Error cleaning up files", e);
        }
    }

    private void deleteFile(final com.google.api.services.drive.model.File file) {
        try {
            listener.getLogger().printf("Deleting %s (%s)%n", file.getName(), file.getId());
            drive.files()
                .delete(file.getId())
                .setSupportsTeamDrives(true)
                .execute();
        } catch (IOException e) {
            listener.error("Error deleting file", e);
        }
    }

}
