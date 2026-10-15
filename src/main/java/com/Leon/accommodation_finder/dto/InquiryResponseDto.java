package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.InquirySubject;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class InquiryResponseDto {

    private Long id;
    private String fullName;
    private String email;
    private InquirySubject subject;
    private String message;
    private LocalDateTime createdAt;
}
