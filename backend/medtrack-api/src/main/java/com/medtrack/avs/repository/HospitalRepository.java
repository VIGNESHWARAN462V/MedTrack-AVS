package com.medtrack.avs.repository;

import com.medtrack.avs.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    Optional<Hospital> findByCode(String code);
    List<Hospital> findByActiveTrue();
    boolean existsByCode(String code);
}
