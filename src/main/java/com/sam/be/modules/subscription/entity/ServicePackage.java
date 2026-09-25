package com.sam.be.modules.subscription.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.common.constant.enums.PackageType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackage extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageType type;

    // Giá tiền cố định (VD: 59000, 149000). Nếu gói giá động thì để trống hoặc bằng 0
    @Column(precision = 15, scale = 2)
    private BigDecimal price;

    // Cờ báo hiệu tính giá theo % (Theo ý sếp)
    @Column(name = "is_dynamic_price", nullable = false)
    @Builder.Default
    private Boolean isDynamicPrice = false;

    // Phẩn trăm thu phí (VD: 3.0 cho 3%). Chỉ dùng khi is_dynamic_price = true
    @Column(name = "percentage_fee")
    private Double percentageFee;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}