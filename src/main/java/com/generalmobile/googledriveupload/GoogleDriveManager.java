package com.generalmobile.googledriveupload;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.google.common.base.Joiner;
import hudson.model.TaskListener;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

class GoogleDriveManager extends ManagerBase {
    
    GoogleDriveManager(final Drive driveService, final TaskListener listener) {
        super(driveService, listener);
    }

    void uploadFolder(java.io.File source, String destFolderName, String userMail) {
        listener.getLogger().printf("userMail %s%n", userMail);
        File destFolder = findDestFolderInDrive(destFolderName, userMail);
        if (destFolder != null) {
            uploadFile(source, destFolder);
        }
    }

    @Override
    protected Optional<File> findInFolderByQuery(final String query){
        try {
            String pageToken = null;
            do {
                FileList result = drive.files().list()
                    .setQ(query)
                    .setPageToken(pageToken)
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
    protected File createNewFolder(File parentFolder, String name) {
        return createNewFolder(Collections.singletonList(parentFolder.getName()),
            Collections.singletonList(parentFolder.getId()), name);
    }

    @Override
    protected DriveRequest<File> createUpdateFileRequest(final File existingFile, final File destFolder, final java.io.File source) throws IOException {
        // Create new File content
        String type = Files.probeContentType(source.toPath());
        FileContent newContentInputStream = new FileContent(type, source);
        File newContent = createNewFile(source.getName(), type);
        // Update existing file with the new File content
        return drive.files()
            .update(existingFile.getId(), newContent, newContentInputStream)
            .setAddParents(destFolder.getId());
    }

    @Override
    protected DriveRequest<File> createNewFileRequest(final File destFolder, final java.io.File source) throws IOException {
        // Create new File content
        String type = Files.probeContentType(source.toPath());
        FileContent newContentInputStream = new FileContent(type, source);
        File newContent = createNewFile(destFolder, source.getName(), type);
        // Create a new file with the new File content
        return drive.files()
            .create(newContent, newContentInputStream);
    }
    
    private File createNewFile(File parentFolder, String name, String type) {
        File file = createNewFile(name, type);
        if (!parentFolder.getId().isEmpty() && !"-".equals(parentFolder.getId())) {
            file.setParents(Collections.singletonList(parentFolder.getId()));
        }
        return file;
    }

    private File createNewFile(String name, String type) {
        File file = new File();
        file.setName(name);
        file.setMimeType(type);
        return file;
    }

    private File findDestFolderInDrive(String destFolderName, String userMail) {
        String[] destinationFolders = destFolderName.split("/");
        File destFolder = controlParent(destinationFolders[0], userMail);
        // Find or create additional subdirs
        for (int i = 1; i < destinationFolders.length && destFolder != null; i++) {
            String subFolderName = destinationFolders[i];
            File newParentFolder = destFolder;
            destFolder = findFolderInFolder(newParentFolder, subFolderName)
                .orElseGet(() -> createNewFolder(Collections.singletonList(newParentFolder.getName()), Collections.singletonList(newParentFolder.getId()),
                    subFolderName));
        }
        return destFolder;
    }
    
    private File controlParent(String destFolderName, String userMail) {
        String[] mails = userMail.split(";");
        try {

            Optional<File> destFolder = findDestFolderDrive(destFolderName);
            if (destFolder.isPresent()){
                return destFolder.get();
            }
            File inserted = createNewFolder(Collections.singletonList("root"), Collections.emptyList(), destFolderName);
            
            BatchRequest batch = drive.batch();
            JsonBatchCallback<Permission> callBack = getPermissionJsonBatchCallback(destFolderName);
            for (String mail : mails) {
                Permission userPermission = new Permission()
                    //                    .setValue(mail)
                    .setType("user")
                    .setRole("writer")
                    .setEmailAddress(mail);
                drive.permissions().create(inserted.getId(), userPermission)
                    .setFields("id")
                    .queue(batch, callBack);
            }
            batch.execute();

            return inserted;
        } catch (IOException e) {
            listener.error("Error accessing Google Drive Folder " + destFolderName, e);
        }
        return null;
    }

    private JsonBatchCallback<Permission> getPermissionJsonBatchCallback(final String destFolderName) {
        return new JsonBatchCallback<Permission>() {
                    @Override
                    public void onSuccess(Permission permission, HttpHeaders httpHeaders) throws IOException {
                        listener.getLogger().printf("Write permissions set to Folder %s for %s%n", destFolderName, permission.getEmailAddress());
                    }
    
                    @Override
                    public void onFailure(GoogleJsonError googleJsonError, HttpHeaders httpHeaders) throws IOException {
                        listener.error("Error assigning permissions to Folder " + destFolderName + " : " + googleJsonError.getMessage());
                    }
                };
    }

    private Optional<File> findDestFolderDrive(final String destFolderName) {
        listener.getLogger().printf("Searching for %s%n", destFolderName);
        return findInFolderByQuery(String.format("mimeType='%s' and name='%s' and trashed=false",
            GOOGLE_DRIVE_FOLDER_MIMETYPE, destFolderName));
    }
    
    private  File createNewFolder(final List<String> parentNames, final List<String> parentIds, final String name){
        // Need to create the folder...
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType(GOOGLE_DRIVE_FOLDER_MIMETYPE);
        fileMetadata.setParents(parentIds);
        try {
            File newFolder = drive.files().create(fileMetadata)
                .setSupportsTeamDrives(true)
                .setFields("id, name, parents")
                .execute();
            listener.getLogger().printf("Created new Folder %s (%s) in %s (%s)%n",
                newFolder.getName(), newFolder.getId(), Joiner.on(",").join(parentNames),  Joiner.on(",").join(parentIds));
            return newFolder;
        } catch (IOException e) {
            listener.error("Error creating folder in Shared Drive", e);
        }
        return null;
    }

    public void cleanup(final String type, final List<String> names) {
        try {
            String pageToken = null;
            do {
                FileList result = drive.files().list()
                    .setPageToken(pageToken)
                    .execute();
                List<com.google.api.services.drive.model.File> files = result.getFiles();
                for (com.google.api.services.drive.model.File file : files) {
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
            drive.files().delete(file.getId()).execute();
        } catch (IOException e) {
            listener.error("Error deleting file", e);
        }
    }

}
