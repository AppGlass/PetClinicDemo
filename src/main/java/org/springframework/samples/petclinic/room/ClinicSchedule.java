package org.springframework.samples.petclinic.room;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class ClinicSchedule {

	static final List<String> CLINICS = List.of("north", "south", "centre");

	private static final int OPENING_HOUR = 8;

	private static final int CLOSING_HOUR = 17;

	private final List<Closure> closures;

	private final Map<Room, List<LocalTime>> sampleBookings;

	private final RoomBookingRepository bookings;

	ClinicSchedule(ObjectMapper mapper, @Value("classpath:rooms/room-closures.json") Resource closures,
			@Value("classpath:rooms/room-bookings.json") Resource sampleBookings, RoomBookingRepository bookings)
			throws IOException {
		this.bookings = bookings;
		try (var input = closures.getInputStream()) {
			this.closures = mapper.readValue(input, new TypeReference<>() {
			});
		}
		try (var input = sampleBookings.getInputStream()) {
			this.sampleBookings = mapper.readValue(input, new TypeReference<>() {
			});
		}
	}

	List<AvailableSlot> available(String clinic, LocalDate day) {
		List<RoomBooking> reservations = this.bookings.findByDay(day);
		List<AvailableSlot> slots = new ArrayList<>();
		for (int hour = OPENING_HOUR; hour < CLOSING_HOUR; hour++) {
			LocalTime time = LocalTime.of(hour, 0);
			for (Room room : Room.values()) {
				if (state(clinic, room, day, time, reservations) == SlotState.FREE) {
					slots.add(new AvailableSlot(room, time));
				}
			}
		}
		return slots;
	}

	List<Hour> hours(LocalDate day) {
		List<RoomBooking> reservations = this.bookings.findByDay(day);
		List<Hour> hours = new ArrayList<>();
		hours.add(hour(day, "00:00", openingTime(), false, reservations));
		for (int hour = OPENING_HOUR; hour < CLOSING_HOUR; hour++) {
			hours.add(hour(day, LocalTime.of(hour, 0).toString(), LocalTime.of(hour + 1, 0).toString(), true,
					reservations));
		}
		hours.add(hour(day, closingTime(), "24:00", false, reservations));
		return hours;
	}

	String openingTime() {
		return LocalTime.of(OPENING_HOUR, 0).toString();
	}

	String closingTime() {
		return LocalTime.of(CLOSING_HOUR, 0).toString();
	}

	private Hour hour(LocalDate day, String from, String through, boolean workingHours, List<RoomBooking> bookings) {
		List<Slot> slots = new ArrayList<>();
		for (String clinic : CLINICS) {
			for (Room room : Room.values()) {
				SlotState state = workingHours ? state(clinic, room, day, LocalTime.parse(from), bookings)
						: SlotState.CLOSED;
				slots.add(new Slot(clinic, room, state));
			}
		}
		return new Hour(from, through, workingHours, slots);
	}

	private SlotState state(String clinic, Room room, LocalDate day, LocalTime time, List<RoomBooking> bookings) {
		return this.closures.stream()
			.filter(closure -> closure.matches(clinic, room, day))
			.map(Closure::state)
			.findFirst()
			.orElse(this.sampleBookings.get(room).contains(time)
					|| bookings.stream().anyMatch(booking -> booking.occupies(clinic, room, time)) ? SlotState.BOOKED
							: SlotState.FREE);
	}

	record AvailableSlot(Room room, LocalTime from, LocalTime through) {
		AvailableSlot(Room room, LocalTime from) {
			this(room, from, from.plusHours(1));
		}
	}

	record Hour(String from, String through, boolean workingHours, List<Slot> slots) {
	}

	record Slot(String clinic, Room room, SlotState state) {
	}

	enum SlotState {

		FREE, BOOKED, MAINTENANCE, CLOSED;

		public String getKey() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	record Closure(String clinic, List<Room> rooms, LocalDate from, LocalDate through, SlotState state) {
		boolean matches(String clinic, Room room, LocalDate day) {
			return this.clinic.equals(clinic) && this.rooms.contains(room) && !day.isBefore(this.from)
					&& !day.isAfter(this.through);
		}
	}

}
