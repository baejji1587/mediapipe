package com.example.demo.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.dto.VideoResponse;
import com.example.demo.service.VideoService;
import com.example.demo.util.FileStorageService;

@Controller
public class MediaController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private VideoService videoService;

    @Value("${processed.dir}")
    private String processedDir;

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a video file to upload.");
            return "redirect:/";
        }

        try {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("video")) {
                VideoResponse videoResponse = videoService.processAndSendFile(file);

                if (videoResponse.isSuccess()) {
                    // FFmpeg를 통해 파일 재인코딩
                    Path reencodedPath = videoService.reencodeVideo(videoResponse.getFilename());
                    
                    redirectAttributes.addFlashAttribute("message", "Video processed and re-encoded successfully.");
                    redirectAttributes.addFlashAttribute("videoUrl", "/processed/" + reencodedPath.getFileName().toString());
                    return "redirect:/video?filename=" + reencodedPath.getFileName().toString();
                } else {
                    redirectAttributes.addFlashAttribute("message", "Video processing failed: " + videoResponse.getErrorMessage());
                }
            } else {
                redirectAttributes.addFlashAttribute("message", "Unsupported file type. Please upload a video file.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "Failed to upload '" + file.getOriginalFilename() + "'");
        }

        return "redirect:/";
    }

    @GetMapping("/processed/{filename}")
    public ResponseEntity<StreamingResponseBody> streamProcessedVideo(@PathVariable("filename") String filename) {
        try {
            Path path = fileStorageService.getProcessedFilePath(filename);
            FileSystemResource resource = new FileSystemResource(path.toFile());

            StreamingResponseBody responseBody = outputStream -> {
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        try {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                        } catch (IOException e) {
                            System.out.println("IOException during streaming: " + e.getMessage());
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };

            return ResponseEntity.ok()
                    .header("Content-Type", "video/mp4")
                    .body(responseBody);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/video")
    public String video(@RequestParam("filename") String filename, Model model) {
        model.addAttribute("videoUrl", "/processed/" + filename);
        return "video";
    }
}
