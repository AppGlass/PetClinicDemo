package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@DataJpaTest
@Import({ ClinicSchedule.class, RoomPolicyStore.class, RoomDecisionService.class, RoomBookingService.class,
		RoomBookingServiceTests.JsonConfiguration.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomBookingServiceTests {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

	private static final LocalTime TIME = LocalTime.of(10, 0);

	@Autowired
	private RoomBookingService service;

	@Autowired
	private RoomBookingRepository bookings;

	@MockitoSpyBean
	private ClinicSchedule schedule;

	@MockitoSpyBean
	private RoomDecisionService decisions;

	@BeforeEach
	void clearBookings() {
		this.bookings.deleteAll();
	}

	@Test
	void bookingPersistsAndUpdatesSearchAndSchedule() {
		assertThat(this.service.book("south", Room.EXAMINATION, DAY, TIME)).isTrue();
		assertThat(this.bookings.findByDay(DAY)).hasSize(1)
			.allMatch(booking -> booking.occupies("south", Room.EXAMINATION, TIME));
		assertThat(this.service.available("south", DAY)).hasSize(5)
			.doesNotContain(new ClinicSchedule.AvailableSlot(Room.EXAMINATION, TIME));
		assertThat(this.schedule.hours(DAY)
			.stream()
			.filter(hour -> hour.from().equals("10:00"))
			.flatMap(hour -> hour.slots().stream())
			.filter(slot -> slot.clinic().equals("south") && slot.room() == Room.EXAMINATION))
			.allMatch(slot -> slot.state() == ClinicSchedule.SlotState.BOOKED);
		assertThat(this.service.book("south", Room.EXAMINATION, DAY, TIME)).isFalse();
	}

	@Test
	void bookingsAtOtherClinicsInOtherRoomsAndOnOtherDaysRemainIndependent() {
		assertThat(this.service.book("south", Room.EXAMINATION, DAY, TIME)).isTrue();
		assertThat(this.service.book("south", Room.SURGERY, DAY, TIME)).isTrue();
		assertThat(this.service.book("centre", Room.EXAMINATION, DAY, TIME)).isTrue();
		assertThat(this.service.book("south", Room.EXAMINATION, DAY.plusDays(1), TIME)).isTrue();
		assertThat(this.bookings.count()).isEqualTo(4);
	}

	@ParameterizedTest
	@CsvSource({ "north,SURGERY,2026-10-01,13:00", "centre,EXAMINATION,2026-10-05,09:00",
			"north,EXAMINATION,2099-06-01,09:00", "south,EXAMINATION,2026-10-01,08:00",
			"south,SURGERY,2026-10-01,09:00", "south,EXAMINATION,2026-10-01,07:00",
			"south,EXAMINATION,2026-10-01,17:00", "south,EXAMINATION,2026-10-01,09:30",
			"south,EXAMINATION,2026-10-01,09:00:00.001" })
	void unavailableOrOffGridSlotsCannotBeBooked(String clinic, Room room, LocalDate day, LocalTime time) {
		assertThat(this.service.book(clinic, room, day, time)).isFalse();
		assertThat(this.bookings.count()).isZero();
	}

	@Test
	void operationalPoliciesApplyToBothTheSearchAndBooking() {
		doReturn(RoomPolicy.Action.BLOCKED).when(this.decisions).decide("south", RoomPolicy.Service.CHECKUP, DAY);
		assertThat(this.service.available("south", DAY)).hasSize(3).allMatch(slot -> slot.room() == Room.SURGERY);
		assertThat(this.service.book("south", Room.EXAMINATION, DAY, TIME)).isFalse();
		assertThat(this.schedule.available("south", DAY)).hasSize(6);
		assertThat(this.bookings.count()).isZero();
	}

	@Test
	void anAllowPolicyCannotBookAPhysicallyClosedRoom() {
		doReturn(RoomPolicy.Action.AVAILABLE).when(this.decisions).decide("north", RoomPolicy.Service.SURGERY, DAY);
		assertThat(this.service.book("north", Room.SURGERY, DAY, LocalTime.of(13, 0))).isFalse();
		assertThat(this.bookings.count()).isZero();
	}

	@Test
	void databaseRejectsDuplicateSlots() {
		this.bookings.saveAndFlush(new RoomBooking("south", Room.EXAMINATION, DAY, TIME));
		assertThatThrownBy(() -> this.bookings.saveAndFlush(new RoomBooking("south", Room.EXAMINATION, DAY, TIME)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(this.bookings.count()).isEqualTo(1);
	}

	@Test
	void concurrentBookingsOfTheSameFreeRoomHaveExactlyOneWinner() throws Exception {
		CyclicBarrier checkedAvailability = new CyclicBarrier(2);
		doAnswer(invocation -> {
			Object slots = invocation.callRealMethod();
			checkedAvailability.await(10, TimeUnit.SECONDS);
			return slots;
		}).when(this.schedule).available("south", DAY);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> this.service.book("south", Room.EXAMINATION, DAY, TIME));
			var second = executor.submit(() -> this.service.book("south", Room.EXAMINATION, DAY, TIME));
			assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
			assertThat(this.bookings.count()).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@TestConfiguration
	static class JsonConfiguration {

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

	}

}
