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

	/**
	 * The slots offered online: those the schedule shows free, in the rooms the booking
	 * rules allow that day.
	 */
	List<ClinicSchedule.AvailableSlot> available(String clinic, LocalDate day) {
		List<ClinicSchedule.AvailableSlot> free = this.schedule.available(clinic, day);
		List<Room> bookable = Arrays.stream(Room.values())
			.filter(room -> this.rules.allowBooking(clinic, room, day))
			.toList();
		List<ClinicSchedule.AvailableSlot> offered = free.stream()
			.filter(slot -> bookable.contains(slot.room()))
			.toList();
		return offered;
	}

	boolean book(String clinic, Room room, LocalDate day, LocalTime start) {
		boolean allowed = this.rules.allowBooking(clinic, room, day);
		boolean free = this.schedule.available(clinic, day).contains(new ClinicSchedule.AvailableSlot(room, start));
		if (!allowed || !free) {
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
