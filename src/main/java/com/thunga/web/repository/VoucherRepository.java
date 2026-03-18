package com.thunga.web.repository;

import com.thunga.web.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByCode(String code);

    List<Voucher> findByCodeIgnoreCase(String code);

    Page<Voucher> findAll(Pageable pageable);

    List<Voucher> findByStatusAndStartDateLessThanEqual(String status, LocalDate date);

    List<Voucher> findByStatusAndEndDateLessThan(String status, LocalDate date);
}