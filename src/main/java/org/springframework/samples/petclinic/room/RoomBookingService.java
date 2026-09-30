package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class RoomBookingService {

	private final RoomDecisionService decisions;

	private final ClinicSchedule schedule;

	private final RoomBookingRepository bookings;

	RoomBookingService(RoomDecisionService decisions, ClinicSchedule schedule, RoomBookingRepository bookings) {
		this.decisions = decisions;
		this.schedule = schedule;
		this.bookings = bookings;
	}

	List<ClinicSchedule.AvailableSlot> available(String clinic, LocalDate day) {
		List<Room> offeredRooms = Arrays.stream(Room.values())
			.filter(room -> this.decisions.decide(clinic, room.service(), day) == RoomPolicy.Action.AVAILABLE)
			.toList();
		return this.schedule.available(clinic, day)
			.stream()
			.filter(slot -> offeredRooms.contains(slot.room()))
			.toList();
	}

	boolean book(String clinic, Room room, LocalDate day, LocalTime start) {
		if (this.decisions.decide(clinic, room.service(), day) != RoomPolicy.Action.AVAILABLE
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
