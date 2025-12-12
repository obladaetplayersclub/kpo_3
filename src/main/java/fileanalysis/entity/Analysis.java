package fileanalysis.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import filestoring.entity.Work;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="reports")
@Data
@NoArgsConstructor
public class Analysis {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name="work_id", nullable = false)
    private Work work;

    @Column(name="plagiarism_detected", nullable = false)
    private Boolean plagiarismDetected;

    @Column(name="file_hash")
    private String fileHash;

    @Column(name="analysis_date")
    @CreationTimestamp
    private LocalDateTime analysisDate;

    @Column(name="details", columnDefinition="TEXT")
    private String details;

}
