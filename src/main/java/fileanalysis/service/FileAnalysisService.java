package fileanalysis.service;

import java.io.IOException;
import java.io.InputStream;
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

import fileanalysis.entity.Analysis;
import fileanalysis.repository.AnalysisRepository;

@Service
public class FileAnalysisService {
    
    private final AnalysisRepository analysisRepository;
    private final WebClient webClient;
    
    @Value("${file.storing.service.url}")
    private String fileStoringServiceUrl;
    
    public FileAnalysisService(AnalysisRepository analysisRepository, WebClient webClient) {
        this.analysisRepository = analysisRepository;
        this.webClient = webClient;
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

    /**
     * Читает файл как текст
     * @param workId ID работы
     * @return текст файла
     */
    public String readFileAsText(Long workId) throws IOException {
        byte[] fileBytes = readFileAsBytes(workId);
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    /**
     * Извлекает слова из текста и подсчитывает их частоту
     * @param text текст файла
     * @return Map с словами и их частотами
     */
    private Map<String, Integer> extractWordFrequencies(String text) {
        Map<String, Integer> wordFreq = new HashMap<>();
        
        // Разбиваем текст на слова, убираем пунктуацию и приводим к нижнему регистру
        String[] words = text.toLowerCase()
            .replaceAll("[^а-яёa-z\\s]", " ") // Оставляем только буквы и пробелы
            .split("\\s+");
        
        // Исключаем короткие слова и стоп-слова
        String[] stopWords = {"и", "в", "на", "с", "по", "для", "от", "до", "из", "к", "о", "а", "как", "что", 
                              "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with"};
        
        for (String word : words) {
            word = word.trim();
            // Пропускаем короткие слова (меньше 3 символов) и стоп-слова
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

    /**
     * Генерирует URL для QuickChart API с облаком слов
     * @param workId ID работы
     * @return URL изображения облака слов
     */
    public String generateWordCloudUrl(Long workId) throws IOException {
        String text = readFileAsText(workId);
        Map<String, Integer> wordFreq = extractWordFrequencies(text);
        
        // Сортируем по частоте и берем топ-50 слов
        List<Map.Entry<String, Integer>> sortedWords = wordFreq.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(50)
            .collect(Collectors.toList());
        
        // Формируем строку для QuickChart: "word1:10,word2:5,word3:3"
        String wordCloudData = sortedWords.stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Collectors.joining(","));
        
        // Формируем URL для QuickChart API
        String encodedData = java.net.URLEncoder.encode(wordCloudData, StandardCharsets.UTF_8);
        return "https://quickchart.io/wordcloud?text=" + encodedData + "&format=png&width=800&height=400";
    }

    /**
     * Получает изображение облака слов
     * @param workId ID работы
     * @return байты изображения PNG
     */
    public byte[] getWordCloudImage(Long workId) throws IOException {
        String wordCloudUrl = generateWordCloudUrl(workId);
        
        byte[] imageBytes = webClient.get()
            .uri(wordCloudUrl)
            .retrieve()
            .bodyToMono(byte[].class)
            .block();
        
        if (imageBytes == null) {
            throw new IOException("Не удалось получить изображение облака слов");
        }
        
        return imageBytes;
    }
}
