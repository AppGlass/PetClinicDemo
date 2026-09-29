package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Online room booking: a slot can be booked when the schedule shows it free and the
 * booking rules allow the room.
 */
@Service
class RoomBookingService {

	private final BookingRules rules;

	private final ClinicSchedule schedule;

	private final RoomBookingRepository bookings;

	RoomBookingService(BookingRules rules, ClinicSchedule schedule, RoomBookingRepository bookings) {
		this.rules = rules;
		this.schedule = schedule;
		this.bookings = bookings;
	}

	List<ClinicSchedule.AvailableSlot> available(String clinic, LocalDate day) {
		List<Room> bookable = Arrays.stream(Room.values())
			.filter(room -> this.rules.allowBooking(clinic, room, day))
			.toList();
		return this.schedule.available(clinic, day).stream().filter(slot -> bookable.contains(slot.room())).toList();
	}

	boolean book(String clinic, Room room, LocalDate day, LocalTime start) {
		if (!this.rules.allowBooking(clinic, room, day)
				|| !this.schedule.available(clinic, day).contains(new ClinicSchedule.AvailableSlot(room, start))) {
			return false;
		}
		try {
			// The unique slot constraint arbitrates requests that passed availability
			// together.
			this.bookings.saveAndFlush(new RoomBooking(clinic, room, day, start));
			return true;
		}
		catch (DataIntegrityViolationException ex) {
			return false;
		}
	}

}
