package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.samples.petclinic.model.BaseEntity;

@Entity
@Table(name = "room_bookings",
		uniqueConstraints = @UniqueConstraint(columnNames = { "clinic", "room", "booking_date", "starts_at" }))
class RoomBooking extends BaseEntity {

	@Column(nullable = false)
	private String clinic;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Room room;

	@Column(name = "booking_date", nullable = false)
	private LocalDate day;

	@Column(name = "starts_at", nullable = false)
	private LocalTime startsAt;

	protected RoomBooking() {
	}

	RoomBooking(String clinic, Room room, LocalDate day, LocalTime startsAt) {
		this.clinic = clinic;
		this.room = room;
		this.day = day;
		this.startsAt = startsAt;
	}

	boolean occupies(String clinic, Room room, LocalTime time) {
		return this.clinic.equals(clinic) && this.room == room && this.startsAt.equals(time);
	}

}
