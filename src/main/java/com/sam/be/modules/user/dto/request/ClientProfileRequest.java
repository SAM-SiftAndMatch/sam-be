package com.sam.be.modules.user.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileRequest {
    @NotBlank(message = "Vui lòng nhập họ tên trên hợp đồng")
    @Size(max = 255)
    private String contractName;

    @NotBlank(message = "Vui lòng nhập email liên hệ")
    @Email
    @Size(max = 255)
    private String contactEmail;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "\\+?[0-9][0-9 ()-]{7,19}", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    @NotNull
    @AssertTrue(message = "Bạn phải chấp nhận Điều khoản Dịch vụ và Chính sách Bảo mật")
    private Boolean acceptedTerms;

    private String companyName;

    private String industry;

    private String websiteUrl;

    private String description;
}
