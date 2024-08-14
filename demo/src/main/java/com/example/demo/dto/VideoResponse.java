package com.example.demo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoResponse {

    private static final Logger logger = LoggerFactory.getLogger(VideoResponse.class);

    private String filename = null;
    private String errorMessage = null;

    public void parseResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            this.errorMessage = "Response body is null or empty.";
            logger.error("Failed to parse response: Response body is null or empty.");
            return;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (jsonResponse.has("filename")) {
                this.filename = jsonResponse.get("filename").asText();
                logger.info("Parsed filename: {}", this.filename);
            } else if (jsonResponse.has("processed_file_url")) {
                this.filename = jsonResponse.get("processed_file_url").asText();  // processed_file_url 필드 처리
                logger.info("Parsed processed_file_url as filename: {}", this.filename);
            } else if (jsonResponse.has("error")) {
                this.errorMessage = jsonResponse.get("error").asText();
                logger.error("Error in response: {}", this.errorMessage);
            } else {
                this.errorMessage = "Unknown error in response: neither 'filename' nor 'error' field found.";
                logger.warn("Response body does not contain 'filename' or 'error' fields. Response: {}", responseBody);
            }
        } catch (Exception e) {
            this.errorMessage = "Failed to parse response due to exception.";
            logger.error("Exception occurred while parsing response body: {}", responseBody, e);
        }
    }

    public boolean isSuccess() {
        return filename != null && errorMessage == null;
    }

    public String getFilename() {
        return filename;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}


