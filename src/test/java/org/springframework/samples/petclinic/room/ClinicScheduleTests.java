package org.springframework.samples.petclinic.room;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;

class ClinicScheduleTests {

	private final RoomBookingRepository bookings = mock(RoomBookingRepository.class);

	private final ClinicSchedule schedule = new ClinicSchedule(new ObjectMapper(),
			new ClassPathResource("rooms/maintenance-calendar.json"), new ClassPathResource("rooms/room-bookings.json"),
			this.bookings);

	ClinicScheduleTests() throws IOException {
	}

	@ParameterizedTest
	@CsvSource({ "2026-08-31,FREE", "2026-09-01,MAINTENANCE", "2026-10-01,MAINTENANCE", "2026-10-31,MAINTENANCE",
			"2026-11-01,FREE" })
	void northMaintenanceClosesOnlySurgeryForItsInclusiveDateWindow(LocalDate day,
			ClinicSchedule.SlotState surgeryState) {
		assertThat(state("north", Room.EXAMINATION, day, "09:00")).isEqualTo(ClinicSchedule.SlotState.FREE);
		assertThat(state("north", Room.SURGERY, day, "13:00")).isEqualTo(surgeryState);
	}

	@Test
	void wholeClinicClosureDoesNotChangeAnotherClinic() {
		LocalDate day = LocalDate.of(2026, 10, 5);
		assertThat(this.schedule.available("centre", day)).isEmpty();
		assertThat(this.schedule.available("south", day)).hasSize(6);
		assertThat(this.schedule.available("centre", day.plusDays(3))).hasSize(3);
	}

	@Test
	void occupiedTimesAreExcludedAndEachAvailableSlotIdentifiesItsRoom() {
		LocalDate day = LocalDate.of(2026, 10, 1);
		assertThat(this.schedule.available("north", day)).containsExactly(
				new ClinicSchedule.AvailableSlot(Room.EXAMINATION, LocalTime.of(9, 0)),
				new ClinicSchedule.AvailableSlot(Room.EXAMINATION, LocalTime.of(10, 0)),
				new ClinicSchedule.AvailableSlot(Room.EXAMINATION, LocalTime.of(11, 0)));
		assertThat(state("north", Room.EXAMINATION, day, "08:00")).isEqualTo(ClinicSchedule.SlotState.BOOKED);
		assertThat(state("south", Room.SURGERY, day, "09:00")).isEqualTo(ClinicSchedule.SlotState.BOOKED);
	}

	@Test
	void reservationChangesOnlyItsClinicRoomAndDay() {
		LocalDate day = LocalDate.of(2026, 10, 1);
		given(this.bookings.findByDay(day))
			.willReturn(List.of(new RoomBooking("south", Room.EXAMINATION, day, LocalTime.of(10, 0))));
		assertThat(state("south", Room.EXAMINATION, day, "10:00")).isEqualTo(ClinicSchedule.SlotState.BOOKED);
		assertThat(state("south", Room.SURGERY, day, "10:00")).isEqualTo(ClinicSchedule.SlotState.FREE);
		assertThat(state("north", Room.EXAMINATION, day, "10:00")).isEqualTo(ClinicSchedule.SlotState.FREE);
		assertThat(state("south", Room.EXAMINATION, day.plusDays(1), "10:00")).isEqualTo(ClinicSchedule.SlotState.FREE);
		assertThat(this.schedule.available("south", day))
			.doesNotContain(new ClinicSchedule.AvailableSlot(Room.EXAMINATION, LocalTime.of(10, 0)));
	}

	@Test
	void outsideWorkingHoursAreClosedEvenDuringMaintenance() {
		List<ClinicSchedule.Hour> hours = this.schedule.hours(LocalDate.of(2026, 10, 1));
		assertThat(hours).hasSize(11);
		assertThat(hours.get(0).from()).isEqualTo("00:00");
		assertThat(hours.get(0).through()).isEqualTo("08:00");
		assertThat(hours.get(10).from()).isEqualTo("17:00");
		assertThat(hours.get(10).through()).isEqualTo("24:00");
		assertThat(hours.get(0).slots()).hasSize(6).allMatch(slot -> slot.state() == ClinicSchedule.SlotState.CLOSED);
		assertThat(hours.get(10).slots()).hasSize(6).allMatch(slot -> slot.state() == ClinicSchedule.SlotState.CLOSED);
		assertThat(hours.stream().filter(ClinicSchedule.Hour::workingHours)).extracting(ClinicSchedule.Hour::from)
			.containsExactly("08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00");
	}

	@Test
	void theCheckedInBookingRulesAgreeWithTheMaintenanceCalendar() throws IOException {
		BookingRules rules = new BookingRules(new ObjectMapper(), new ClassPathResource("rooms/booking-rules.json"));
		for (String clinic : ClinicSchedule.CLINICS) {
			for (String date : List.of("2019-06-01", "2026-08-31", "2026-09-01", "2026-10-01", "2026-10-05",
					"2026-10-08", "2026-10-31", "2026-11-01", "2099-06-01")) {
				LocalDate day = LocalDate.parse(date);
				for (Room room : Room.values()) {
					boolean free = this.schedule.available(clinic, day).stream().anyMatch(slot -> slot.room() == room);
					assertThat(rules.allowBooking(clinic, room, day)).as("%s %s on %s", clinic, room, day)
						.isEqualTo(free);
				}
			}
		}
	}

	private ClinicSchedule.SlotState state(String clinic, Room room, LocalDate day, String time) {
		return this.schedule.hours(day)
			.stream()
			.filter(hour -> hour.from().equals(time))
			.flatMap(hour -> hour.slots().stream())
			.filter(slot -> slot.clinic().equals(clinic) && slot.room() == room)
			.findFirst()
			.orElseThrow()
			.state();
	}

}
