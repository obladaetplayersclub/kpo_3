package filestoring.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import filestoring.entity.Work;
import filestoring.repository.WorkRepository;

@Service
public class FileStorageService {

    private final WorkRepository workRepository;

    @Value("${file.storage.path}")
    private String storagePath;

    public FileStorageService(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    public Work saveFile(MultipartFile file, String studentName, String assignmentName) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
            : "";
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        Path storageDir = Paths.get(storagePath);
        
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        Path filePath = storageDir.resolve(uniqueFilename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Work work = new Work();
        work.setStudentName(studentName);
        work.setAssignmentName(assignmentName);
        work.setFilePath(filePath.toString());

        return workRepository.save(work);
    }

    public Resource getFile(Long workId) throws IOException {
        Work work = workRepository.findById(workId)
            .orElseThrow(() -> new RuntimeException("Работа с ID " + workId + " не найдена"));


        Path filePath = Paths.get(work.getFilePath());

        if (!Files.exists(filePath)) {
            throw new RuntimeException("Файл не найден по пути: " + work.getFilePath());
        }

        Resource resource = new UrlResource(filePath.toUri());
        
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Не удалось прочитать файл: " + work.getFilePath());
        }

        return resource;
    }

}
