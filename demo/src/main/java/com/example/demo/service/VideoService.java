package com.example.demo.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.VideoResponse;
import com.example.demo.util.FileStorageService;

@Service
public class VideoService {

    @Value("${flask.url}")
    private String flaskUrl;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Autowired
    private FileStorageService fileStorageService;

    public VideoResponse processAndSendFile(MultipartFile file) throws IOException {
        Path path = fileStorageService.storeFile(file);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(30000);

        RestTemplate restTemplate = new RestTemplate(factory);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(path.toFile()));

        // Flask 서버의 /upload 엔드포인트로 POST 요청
        ResponseEntity<String> response = restTemplate.postForEntity(flaskUrl + "/upload", body, String.class);

        VideoResponse videoResponse = new VideoResponse();

        if (response.getStatusCode() == HttpStatus.OK) {
            // 비디오 처리 요청을 Flask 서버의 /process 엔드포인트로 전송
            Map<String, String> processRequest = new HashMap<>();
            processRequest.put("filename", path.getFileName().toString());
            ResponseEntity<String> processResponse = restTemplate.postForEntity(flaskUrl + "/process", processRequest, String.class);

            if (processResponse.getStatusCode() == HttpStatus.OK) {
                videoResponse.parseResponse(processResponse.getBody());
            } else {
                videoResponse.setErrorMessage("File processing failed: " + processResponse.getBody());
            }
        } else {
            videoResponse.setErrorMessage("File upload failed: " + response.getBody());
        }

        return videoResponse;
    }


    public Path reencodeVideo(String filename) throws IOException, InterruptedException {
        Path inputPath = fileStorageService.getProcessedFilePath(filename);
        Path outputPath = Paths.get(inputPath.getParent().toString(), "reencoded_" + filename);

        String command = String.format("%s -i %s -vcodec libx264 -acodec aac %s", 
                                       ffmpegPath, inputPath.toString(), outputPath.toString());

        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("FFmpeg re-encoding failed with exit code: " + exitCode);
        }

        return outputPath;
    }
}
