package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.InquirySubject;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class InquiryRequestDto {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name can be at most 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email can be at most 150 characters")
    private String email;

    // one of: General question, Booking help, Payment help, Landlord account, Report a problem
    @NotNull(message = "Subject is required")
    private InquirySubject subject;

    @NotBlank(message = "Message is required")
    @Size(max = 500, message = "Message can be at most 500 characters")
    private String message;
}
