package com.smart.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "specializations",
        uniqueConstraints = @UniqueConstraint(name = "uk_specializations_name", columnNames = "name")
)
public class Specialization extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @OneToMany(mappedBy = "specialization", fetch = FetchType.LAZY)
    private List<Doctor> doctors = new ArrayList<>();

    protected Specialization() {
    }

    public Specialization(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void addDoctor(Doctor doctor) {
        doctors.add(doctor);
        doctor.setSpecialization(this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Doctor> getDoctors() {
        return doctors;
    }
}
