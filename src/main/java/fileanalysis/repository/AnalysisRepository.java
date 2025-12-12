package fileanalysis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fileanalysis.entity.Analysis;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    List<Analysis> findByWork_Id(Long workId);
    
    List<Analysis> findByFileHash(String fileHash);
}