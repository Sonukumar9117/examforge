package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** An exam family students prepare for (UPSC, SSC, Banking …). Managed by admins, never hard-coded in clients. */
@Entity
@Table(name = "exam_categories")
@Getter
@Setter
public class ExamCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Column(length = 50)
    private String icon;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany
    @JoinTable(name = "category_subjects",
            joinColumns = @JoinColumn(name = "category_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_id"))
    @OrderBy("name ASC")
    private Set<Subject> subjects = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExamCategory() {}

    public ExamCategory(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

}
