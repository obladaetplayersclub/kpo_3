package fileanalysis.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fileanalysis.entity.Analysis;
import fileanalysis.service.FileAnalysisService;



@RestController
@RequestMapping("/reports")
public class FileAnalysisController {
    private final FileAnalysisService fileAnalysisService;

    public FileAnalysisController(FileAnalysisService fileAnalysisService) {
        this.fileAnalysisService = fileAnalysisService;
    }

    @GetMapping("/{workId}")
    public ResponseEntity<List<Analysis>> getReports(@PathVariable Long workId) {
        try{
            List<Analysis> reports = fileAnalysisService.getReportsByWorkId(workId);
            return ResponseEntity.ok(reports);
        } catch (Exception e){
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/analyze/{workId}")
    public ResponseEntity<Analysis> postAnalyseReports(@PathVariable Long workId) {
        try {
            Analysis analysis = fileAnalysisService.analyzeFile(workId);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{workId}/wordcloud")
    public ResponseEntity<byte[]> getWordCloud(@PathVariable Long workId) {
        try {
            byte[] imageBytes = fileAnalysisService.getWordCloudImage(workId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", "wordcloud_" + workId + ".png");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(imageBytes);
        } catch (java.io.IOException e) {
            
            System.err.println("Ошибка при генерации облака слов для workId=" + workId + ": " + e.getMessage());
           
            if (e.getMessage() != null && (e.getMessage().contains("не найден") || e.getMessage().contains("Недостаточно слов"))) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            System.err.println("Неожиданная ошибка при генерации облака слов для workId=" + workId + ": " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
    
}
