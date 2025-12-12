package fileanalysis.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import fileanalysis.entity.Analysis;
import fileanalysis.repository.AnalysisRepository;

@Service
public class FileAnalysisService {
    
    private final AnalysisRepository analysisRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${file.storing.service.url}")
    private String fileStoringServiceUrl;
    
    public FileAnalysisService(AnalysisRepository analysisRepository, WebClient webClient, ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public byte[] readFileAsBytes(Long workId) throws IOException {
        Resource resource = webClient.get()
            .uri(fileStoringServiceUrl + "/files/" + workId)
            .retrieve()
            .bodyToMono(Resource.class)
            .block();
        
        if (resource == null || !resource.exists()) {
            throw new IOException("Файл не найден для workId: " + workId);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    public String hashString(byte[] fileBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при вычислении хеша", e);
        }
    }

    public Analysis analyzeFile(Long workId) throws IOException {
        byte[] fileBytes = readFileAsBytes(workId);
        String fileHash = hashString(fileBytes);
        List<Analysis> existingReports = analysisRepository.findByFileHash(fileHash);
        boolean plagiarismDetected = false;
        String details = null;
        if (!existingReports.isEmpty()) {
            for (Analysis existingReport : existingReports) {
                long existingWorkId = existingReport.getWork().getId();
                if (existingWorkId != workId) {
                    plagiarismDetected = true;
                    details = "Найден дубликат файла в работе ID: " + existingWorkId;
                    break;
                }
            }
        }
        
        filestoring.entity.Work work = new filestoring.entity.Work();
        work.setId(workId);
        Analysis analysis = new Analysis();
        analysis.setWork(work);
        analysis.setPlagiarismDetected(plagiarismDetected);
        analysis.setFileHash(fileHash);
        analysis.setDetails(details);
        return analysisRepository.save(analysis);
    }

    public List<Analysis> getReportsByWorkId(Long workId) {
        return analysisRepository.findByWork_Id(workId);
    }

    public String readFileAsText(Long workId) throws IOException {
        byte[] fileBytes = readFileAsBytes(workId);
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    private Map<String, Integer> extractWordFrequencies(String text) {
        Map<String, Integer> wordFreq = new HashMap<>();
        
        if (text == null || text.trim().isEmpty()) {
            return wordFreq;
        }

        String cleanedText = text.toLowerCase()
            .replaceAll("[^\\p{IsCyrillic}\\p{IsLatin}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        
        if (cleanedText.isEmpty()) {
            return wordFreq;
        }
        
        String[] words = cleanedText.split("\\s+");
        
        String[] stopWords = {"и", "в", "на", "с", "по", "для", "от", "до", "из", "к", "о", "а", "как", "что", 
                              "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with"};
        
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 3 && !isStopWord(word, stopWords)) {
                wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
            }
        }
        
        return wordFreq;
    }

    private boolean isStopWord(String word, String[] stopWords) {
        for (String stopWord : stopWords) {
            if (word.equals(stopWord)) {
                return true;
            }
        }
        return false;
    }

    private String generateWordCloudText(Long workId) throws IOException {
        String text = readFileAsText(workId);
        Map<String, Integer> wordFreq = extractWordFrequencies(text);
        
        if (wordFreq.isEmpty()) {
            throw new IOException("Недостаточно слов для генерации облака слов.");
        }
        
        List<Map.Entry<String, Integer>> sortedWords = wordFreq.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(50)
            .collect(Collectors.toList());

        // Формируем строку для QuickChart wordCloud API: "word1:count1,word2:count2"
        String wordCloudText = sortedWords.stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Collectors.joining(","));
        
        return wordCloudText;
    }

    public byte[] getWordCloudImage(Long workId) throws IOException {
        String wordCloudText = generateWordCloudText(workId);
        
        try {
            // Формируем URL для QuickChart wordCloud API
            // Формат: https://quickchart.io/wordcloud?text=word1:count1,word2:count2&format=png&width=800&height=400
            String encodedText = java.net.URLEncoder.encode(wordCloudText, StandardCharsets.UTF_8);
            String url = String.format(
                "https://quickchart.io/wordcloud?text=%s&format=png&width=800&height=400&backgroundColor=white",
                encodedText
            );
            
            byte[] imageBytes = webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
            
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IOException("Не удалось получить изображение облака слов: пустой ответ");
            }
            
            // Проверяем, что это действительно PNG (начинается с PNG signature)
            if (imageBytes.length >= 8 && 
                imageBytes[0] == (byte)0x89 && imageBytes[1] == 'P' && 
                imageBytes[2] == 'N' && imageBytes[3] == 'G') {
                return imageBytes;
            }
            
            throw new IOException("Не удалось получить изображение облака слов: неверный формат ответа");
        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            throw new IOException("Ошибка QuickChart API: " + e.getStatusCode() + " body: " + 
                (errorBody != null && errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody), e);
        } catch (Exception e) {
            throw new IOException("Не удалось получить изображение облака слов: " + e.getMessage(), e);
        }
    }
}