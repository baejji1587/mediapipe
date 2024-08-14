package com.example.demo.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    @Value("${upload.dir}")
    private String uploadDir;

    @Value("${processed.dir}")
    private String processedDir;

    @PostConstruct
    public void init() {
        try {
            Path processedPath = Paths.get(processedDir);
            if (!Files.exists(processedPath)) {
                Files.createDirectories(processedPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create processed directory!", e);
        }
    }

    public Path storeFile(MultipartFile file) throws IOException {
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        Path path = Paths.get(uploadDir, filename);
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());
        return path;
    }

    public Path storeProcessedFile(MultipartFile file) throws IOException {
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        Path path = Paths.get(processedDir, filename);
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());

        System.out.println("Processed file stored at: " + path.toString());

        return path;
    }

    public Path getProcessedFilePath(String filename) throws NoSuchFileException {
        Path path = Paths.get(processedDir, filename);
        if (!Files.exists(path)) {
            throw new NoSuchFileException("File not found: " + filename);
        }
        return path;
    }

    public ResponseEntity<?> streamVideo(Path path) throws IOException {
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
                        System.err.println("Streaming error: " + e.getMessage());
                        break;
                    }
                }
            }
        };

        return ResponseEntity.ok()
                .header("Content-Type", "video/mp4")
                .body(responseBody);
    }

    public void storeProcessedFile(FileSystemResource fileSystemResource) throws IOException {
        Path path = Paths.get(processedDir, fileSystemResource.getFilename());
        Files.createDirectories(path.getParent());

        try (InputStream inputStream = fileSystemResource.getInputStream()) {
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Processed file stored at: " + path.toString());
    }

    public Path saveFile(MultipartFile file) throws IOException {
        return storeFile(file);
    }

    // 추가: FFmpeg을 사용하여 비디오 코덱을 변경하는 메소드
    public void convertVideoCodec(Path inputPath, Path outputPath, String codec) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
            "ffmpeg", "-i", inputPath.toString(), "-vcodec", codec, outputPath.toString()
        );
        builder.redirectErrorStream(true);  // 표준 오류를 표준 출력으로 리다이렉트
        Process process = builder.start();

        // 비동기적으로 스트림을 읽어오는 쓰레드 생성
        Thread outputThread = new Thread(() -> {
            try (InputStream stream = process.getInputStream()) {
                stream.transferTo(System.out);
            } catch (IOException e) {
                throw new RuntimeException("Error reading process output", e);
            }
        });
        outputThread.start();

        int exitCode = process.waitFor();
        outputThread.join();  // 스트림 처리가 완료될 때까지 대기

        if (exitCode != 0) {
            throw new IOException("FFmpeg re-encoding failed with exit code: " + exitCode);
        }
        System.out.println("Video codec converted and saved to: " + outputPath.toString());
    }

}
