package filestoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "filestoring")
public class FileStoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileStoringApplication.class, args);
    }
}
