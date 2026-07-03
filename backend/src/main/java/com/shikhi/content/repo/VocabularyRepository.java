package com.shikhi.content.repo;

import com.shikhi.content.domain.Vocabulary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabularyRepository extends JpaRepository<Vocabulary, UUID> {

	List<Vocabulary> findByCefrLevelOrderByOrdinal(String cefrLevel);

	long countByCefrLevel(String cefrLevel);
}
