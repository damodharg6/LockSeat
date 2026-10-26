package com.damodhar.seatlock.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seat_seq")
    @SequenceGenerator(name = "seat_seq", sequenceName = "seat_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "row_label", nullable = false, length = 3)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    protected Seat() {
    }

    public Seat(Section section, String rowLabel, int seatNumber) {
        this.section = section;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
    }

    public Long getId() {
        return id;
    }

    public Section getSection() {
        return section;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
}
