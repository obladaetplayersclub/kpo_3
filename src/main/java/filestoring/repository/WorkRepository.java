package filestoring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import filestoring.entity.Work;

@Repository 
public interface WorkRepository extends JpaRepository<Work, Long> {}
