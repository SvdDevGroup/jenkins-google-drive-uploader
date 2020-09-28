/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

import java.util.LinkedList;
import java.util.Queue;

import static com.generalmobile.googledriveupload.GoogleDriveManagerTest.FILE_ID;
import static com.generalmobile.googledriveupload.GoogleDriveManagerTest.FOLDER_ID;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DriveMockHttpTransport extends MockHttpTransport {
    int requestCount = 0;
    
    interface MyMockResponse {
        MockLowLevelHttpResponse response();
    }

    static class MockRequest {
        String method;
        String url;

        public MockRequest(final String method, final String url) {
            this.method = method;
            this.url = url;
        }
    }
        
    static class BaseMockRequest {
        static public MockResponse createResponse(final MockRequest request, final MyMockResponse myMockResponse){
            return new MockResponse(request, myMockResponse.response());
        }
    }
    
    static class MockResponse {
        MockRequest expectedRequest;
        MockLowLevelHttpResponse response;

        public MockResponse(final MockRequest request, final MockLowLevelHttpResponse response) {
            this.expectedRequest = request;
            this.response = response;
        }
    }
    
    private Queue<MockResponse> mockedResponsesQueue = new LinkedList<>();
    
    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) {
        if ( mockedResponsesQueue.isEmpty()){
            fail("Unexpect google api call " + method + " " +  url);
        }
        MockResponse mockResponse = mockedResponsesQueue.remove();
        requestCount++;
        String errormessage = "Request (" +  requestCount + ") :\nactual : " + method + " " + url + 
            "\nexpected :" + mockResponse.expectedRequest.method + " " + mockResponse.expectedRequest.url; 
        assertEquals(errormessage, mockResponse.expectedRequest.method, method);
        assertTrue(errormessage, url.startsWith(mockResponse.expectedRequest.url));
        return new MockLowLevelHttpRequest() {
            @Override
            public LowLevelHttpResponse execute() {
                return mockResponse.response;
            }
        };
    }

    public void mock(MockResponse mockResponse) {
        mockedResponsesQueue.add(mockResponse);
    }

    static class FilesListRequest extends BaseMockRequest {
        static final MockRequest request = new MockRequest("GET", "https://www.googleapis.com/drive/v3/files");

        static public MockResponse emptyList() {
            return createResponse(request, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{ \"kind\": \"drive#fileList\", \"incompleteSearch\": false, \"files\": []}");
                return response;
            });
        }

        static public MockResponse findFolder(String id, String name) {
            return createResponse(request, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{ \"kind\": \"drive#fileList\", \"incompleteSearch\": false, \"files\": [ " +
                    "{\n" +
                    "  \"kind\": \"drive#file\",\n" +
                    "  \"id\": \"" + id + "\",\n" +
                    "  \"name\": \"" + name + "\",\n" +
                    "  \"mimeType\": \"application/vnd.google-apps.folder\" }"
                    +"]}");
                return response;
            });
        }

        static public MockResponse findFile(String id, String name, String type) {
            return createResponse(request, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{ \"kind\": \"drive#fileList\", \"incompleteSearch\": false, \"files\": [ " +
                    "{\n" +
                    "  \"kind\": \"drive#file\",\n" +
                    "  \"id\": \"" + id + "\",\n" +
                    "  \"name\": \"" + name + "\",\n" +
                    "  \"mimeType\": \""+ type +"\" }"
                    +"]}");
                return response;
            });
        }
    }

    static class FilesCreateRequest extends BaseMockRequest {

        static final MockRequest request = new MockRequest("POST", "https://www.googleapis.com/drive/v3/files");

        static public MockResponse createFolder(String id, String name) {
            return createResponse(request, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{\n" +
                    "  \"kind\": \"drive#file\",\n" +
                    "  \"id\": \"" + id + "\",\n" +
                    "  \"name\": \"" + name + "\",\n" +
                    "  \"mimeType\": \"application/vnd.google-apps.folder\" }");
                return response;
            });
        }
    }

    static class FilesUploadRequest extends BaseMockRequest {
        static final MockRequest postRequest = new MockRequest("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable");
        static final MockRequest patchRequest = new MockRequest("PATCH", "https://www.googleapis.com/upload/drive/v3/files/" + FILE_ID +"?addParents=" + FOLDER_ID + "&uploadType=resumable");
        static final MockRequest putRequest = new MockRequest("PUT", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=");

        static public MockResponse initiateCreateUpload(String id, String name, String type) {
            return createResponse(postRequest, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.addHeader("Location", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=xa298sd_sdlkj2");
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{\n" +
                    "  \"kind\": \"drive#file\",\n" +
                    "  \"id\": \"" + id + "\",\n" +
                    "  \"name\": \"" + name + "\",\n" +
                    "  \"mimeType\": \"" + type + "\" }");
                return response;
            });
        }

        static public MockResponse initiateUpdateUpload(String id, String name, String type) {
            return createResponse(patchRequest, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.addHeader("Location", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=xa298sd_sdlkj2");
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{\n" +
                    "  \"kind\": \"drive#file\",\n" +
                    "  \"id\": \"" + id + "\",\n" +
                    "  \"name\": \"" + name + "\",\n" +
                    "  \"mimeType\": \"" + type + "\" }");
                return response;
            });
        }

        static public MockResponse resumeUpload(String id, String name, String type) {
            return createResponse(putRequest, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.setContentType(Json.MEDIA_TYPE);
                response.setContent("{\n" +
                    "  \"kind\": \"drive#file\",\n" +
                    "  \"id\": \"" + id + "\",\n" +
                    "  \"name\": \"" + name + "\",\n" +
                    "  \"mimeType\": \"" + type + "\" }");
                return response;
            });
        }
    }

    static class BatchCreateRequest extends BaseMockRequest {

        static final MockRequest request = new MockRequest("POST", "https://www.googleapis.com/batch/drive/v3");

        static public MockResponse acceptedPermissions(String id, String email) {
            return createResponse(request, () -> {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(200);
                response.setContentType("multipart/mixed; boundary=END_OF_PART");
                response.setContent("--END_OF_PART\n" +
                    "Content-Type: application/http\n" +
                    "Content-ID: response-1\n" +
                    "\n" +
                    "HTTP/1.1 200 OK\n" +
                    "Content-Type: application/json; charset=UTF-8\n" +
                    "Date: Fri, 13 Nov 2015 19:28:59 GMT\n" +
                    "Expires: Fri, 13 Nov 2015 19:28:59 GMT\n" +
                    "Cache-Control: private, max-age=0\n" +
                    "Content-Length: -1\n" +
                    "\n" +
                    "\n" +
                    "\n" +
                    "{\n" +
                    " \"id\": \"" + id + "\",\n" +
                    " \"emailAddress\": \"" + email + "\"\n" +
                    "}\n" +
                    "\n" +
                    "\n" +
                    "\n" +
                    "--END_OF_PART--\n");
                return response;
            });
        }
    }
}
