package com.ylcare.nursing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class MediaStorage {
    private final Path root;

    public MediaStorage(@Value("${yl.media.dir:./data/media}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String save(MultipartFile file, String prefix) throws IOException {
        String original = file.getOriginalFilename() == null ? "bin" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot) : "";
        String name = prefix + "-" + UUID.randomUUID() + ext;
        Path dest = root.resolve(name);
        file.transferTo(dest);
        return dest.toString();
    }

    public Path resolve(String stored) {
        return Path.of(stored);
    }
}
