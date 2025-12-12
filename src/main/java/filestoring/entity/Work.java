package filestoring.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="works")
@Data
@NoArgsConstructor
public class Work {
    @Id
    @GeneratedValue
    private long id;

    @Column(name="student_name", nullable=false)
    private String studentName;

    @Column(name="assignment_name", nullable=false)
    private String assignmentName;

    @Column(name="file_path", nullable=false)
    private String filePath;

    @Column(name="uploaded_at")
    @CreationTimestamp
    private LocalDateTime uploadedAt;

}
