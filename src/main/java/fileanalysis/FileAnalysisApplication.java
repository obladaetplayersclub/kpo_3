package fileanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("fileanalysis")
public class FileAnalysisApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileAnalysisApplication.class, args);
    }

}
