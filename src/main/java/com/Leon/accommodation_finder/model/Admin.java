package com.Leon.accommodation_finder.model;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Admin extends User {
    // no additional fields for now
}
