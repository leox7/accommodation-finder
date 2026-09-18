package com.Leon.accommodation_finder.model;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor

public class Student extends User {

    @NotBlank(message = "University is required")
    private String university;
}
