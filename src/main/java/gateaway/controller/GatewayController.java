package gateaway.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;



@RestController
@RequestMapping("/api")
@Tag(name = "API Gateway", description = "API Gateway для системы антиплагиата")
public class GatewayController {
    private final WebClient webClient;

    public GatewayController(WebClient webClient){
        this.webClient = webClient;
    }

    @Value("${file.storing.service.url}")
    private String fileStoringServiceUrl;

    @Value("${file.analysis.service.url}")
    private String fileAnalysisServiceUrl;

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить файл", description = "Загружает файл работы студента. После загрузки автоматически запускается анализ.")
    @ApiResponse(responseCode = "200", description = "Файл успешно загружен")
    @ApiResponse(responseCode = "503", description = "Сервис недоступен")
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "Файл для загрузки", required = true) 
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Имя студента", required = true) 
            @RequestParam("studentName") String studentName,
            @Parameter(description = "Название задания", required = true) 
            @RequestParam("assignmentName") String assignmentName) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file.getResource());
            body.add("studentName", studentName);
            body.add("assignmentName", assignmentName);
            
            Object response = webClient.post()
                .uri(fileStoringServiceUrl + "/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
            
            if (response != null) {
                try {
                    Long workId = extractWorkId(response);
                    if (workId != null) {
                       
                        webClient.post()
                            .uri(fileAnalysisServiceUrl + "/reports/analyze/" + workId)
                            .retrieve()
                            .bodyToMono(Object.class)
                            .subscribe(); 
                    }
                } catch (Exception e) {}
            }
            return ResponseEntity.ok(response);
        } catch (WebClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            return ResponseEntity.status(503).build();
        }
    }

    @SuppressWarnings("unchecked")
    private Long extractWorkId(Object response) {
        try {
            if (response instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) response;
                Object idObj = map.get("id");
                if (idObj instanceof Number) {
                    return ((Number) idObj).longValue();
                }
            }
        } catch (Exception e) {
        
        }
        return null;
    }

    @GetMapping("/files/{id}")
    @Operation(summary = "Получить файл", description = "Получает файл по ID работы")
    @ApiResponse(responseCode = "200", description = "Файл найден")
    @ApiResponse(responseCode = "404", description = "Файл не найден")
    @ApiResponse(responseCode = "503", description = "Сервис недоступен")
    public ResponseEntity<Resource> getFile(
            @Parameter(description = "ID работы") @PathVariable Long id) {
        try {
            Resource resource = webClient.get()
                .uri(fileStoringServiceUrl + "/files/" + id)
                .retrieve()
                .bodyToMono(Resource.class)
                .block();
            
            if (resource == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(resource);
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(503).build();
        }
    }

    @GetMapping("/reports/{workId}")
    @Operation(summary = "Получить отчеты", description = "Получает все отчеты анализа для работы")
    @ApiResponse(responseCode = "200", description = "Отчеты найдены")
    @ApiResponse(responseCode = "404", description = "Отчеты не найдены")
    @ApiResponse(responseCode = "503", description = "Сервис недоступен")
    public ResponseEntity<?> getReports(
            @Parameter(description = "ID работы") @PathVariable Long workId) {
        try {
            List<?> reports = webClient.get()
                .uri(fileAnalysisServiceUrl + "/reports/" + workId)
                .retrieve()
                .bodyToFlux(Object.class)
                .collectList()
                .block();
            
            if (reports == null || reports.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(reports);
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(503).build();
        }
    }

    @PostMapping("/reports/analyze/{workId}")
    @Operation(summary = "Запустить анализ", description = "Запускает анализ файла на плагиат")
    @ApiResponse(responseCode = "200", description = "Анализ выполнен")
    @ApiResponse(responseCode = "500", description = "Ошибка при анализе")
    @ApiResponse(responseCode = "503", description = "Сервис недоступен")
    public ResponseEntity<?> analyzeFile(
            @Parameter(description = "ID работы для анализа") @PathVariable Long workId) {
        try {
            Object analysis = webClient.post()
                .uri(fileAnalysisServiceUrl + "/reports/analyze/" + workId)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
            
            if (analysis == null) {
                return ResponseEntity.status(500).build();
            }
            return ResponseEntity.ok(analysis);
        } catch (WebClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            return ResponseEntity.status(503).build();
        }
    }

    @GetMapping("/reports/{workId}/wordcloud")
    @Operation(summary = "Получить облако слов", description = "Генерирует и возвращает изображение облака слов для работы")
    @ApiResponse(responseCode = "200", description = "Облако слов успешно сгенерировано")
    @ApiResponse(responseCode = "404", description = "Работа не найдена")
    @ApiResponse(responseCode = "503", description = "Сервис недоступен")
    public ResponseEntity<byte[]> getWordCloud(
            @Parameter(description = "ID работы") @PathVariable Long workId) {
        try {
            byte[] imageBytes = webClient.get()
                .uri(fileAnalysisServiceUrl + "/reports/" + workId + "/wordcloud")
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
            
            if (imageBytes == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .body(imageBytes);
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(503).build();
        }
    }
}
