package com.smart.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "doctors",
        uniqueConstraints = @UniqueConstraint(name = "uk_doctors_user", columnNames = "user_id"),
        indexes = {
                @Index(name = "idx_doctors_specialization", columnList = "specialization_id"),
                @Index(name = "idx_doctors_last_name", columnList = "last_name")
        }
)
public class Doctor extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialization_id", nullable = false)
    private Specialization specialization;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "office_number", length = 30)
    private String officeNumber;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "doctor", fetch = FetchType.LAZY)
    private List<WorkingSchedule> workingSchedules = new ArrayList<>();

    @OneToMany(mappedBy = "doctor", fetch = FetchType.LAZY)
    private List<Appointment> appointments = new ArrayList<>();

    protected Doctor() {
    }

    public Doctor(User user, Specialization specialization, String firstName, String lastName) {
        this.user = user;
        this.specialization = specialization;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void addWorkingSchedule(WorkingSchedule schedule) {
        workingSchedules.add(schedule);
        schedule.setDoctor(this);
    }

    public void addAppointment(Appointment appointment) {
        appointments.add(appointment);
        appointment.setDoctor(this);
    }

    public String getFullName() {
        StringBuilder fullName = new StringBuilder();
        if (lastName != null && !lastName.isBlank()) {
            fullName.append(lastName);
        }
        if (firstName != null && !firstName.isBlank()) {
            if (!fullName.isEmpty()) {
                fullName.append(' ');
            }
            fullName.append(firstName);
        }
        if (middleName != null && !middleName.isBlank()) {
            if (!fullName.isEmpty()) {
                fullName.append(' ');
            }
            fullName.append(middleName);
        }
        return fullName.toString();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Specialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(Specialization specialization) {
        this.specialization = specialization;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOfficeNumber() {
        return officeNumber;
    }

    public void setOfficeNumber(String officeNumber) {
        this.officeNumber = officeNumber;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<WorkingSchedule> getWorkingSchedules() {
        return workingSchedules;
    }

    public List<Appointment> getAppointments() {
        return appointments;
    }
}
