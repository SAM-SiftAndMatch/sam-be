package com.sam.be.modules.subscription.repository;

import com.sam.be.modules.subscription.entity.ServicePackage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {}
