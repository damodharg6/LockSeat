package com.damodhar.seatlock.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.damodhar.seatlock.venue.Section;

@Entity
@Table(name = "event_section_price")
public class EventSectionPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    protected EventSectionPrice() {
    }

    public EventSectionPrice(Event event, Section section, long pricePaise) {
        this.event = event;
        this.section = section;
        this.pricePaise = pricePaise;
    }

    public Section getSection() {
        return section;
    }

    public long getPricePaise() {
        return pricePaise;
    }
}
