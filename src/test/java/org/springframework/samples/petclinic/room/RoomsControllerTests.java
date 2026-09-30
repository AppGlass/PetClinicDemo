package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomsController.class)
@Import(ClinicSchedule.class)
@DisabledInNativeImage
@DisabledInAotMode
class RoomsControllerTests {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RoomBookingService bookings;

	@MockitoBean
	private RoomBookingRepository repository;

	@Test
	void blockedSearchOffersScheduleNavigationWithoutAnOwnerOrVisit() throws Exception {
		this.mvc.perform(get("/petclinic/rooms").contextPath("/petclinic").param("date", DAY.toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("No slots available.")))
			.andExpect(content().string(containsString("Find available rooms")))
			.andExpect(content().string(containsString("formaction=\"/petclinic/rooms/schedule\"")))
			.andExpect(content().string(not(containsString("ownerId"))))
			.andExpect(content().string(not(containsString("name=\"service\""))))
			.andExpect(model().attribute("clinics", List.of("north", "south", "centre")));
	}

	@Test
	void resultsIdentifyTheRoomAndTimeAndPostToTheBookingAction() throws Exception {
		given(this.bookings.available("south", DAY))
			.willReturn(List.of(new ClinicSchedule.AvailableSlot(Room.EXAMINATION, LocalTime.of(10, 0)),
					new ClinicSchedule.AvailableSlot(Room.SURGERY, LocalTime.of(10, 0))));
		this.mvc
			.perform(get("/petclinic/rooms").contextPath("/petclinic")
				.param("clinic", "south")
				.param("date", DAY.toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Room 1 · Examination")))
			.andExpect(content().string(containsString("Room 2 · Surgery")))
			.andExpect(content().string(containsString("10:00–11:00")))
			.andExpect(content().string(containsString("action=\"/petclinic/rooms/book\"")))
			.andExpect(content().string(containsString("name=\"room\" value=\"SURGERY\"")))
			.andExpect(content().string(containsString("Book room")));
		this.mvc.perform(get("/rooms/availability").param("clinic", "south").param("date", DAY.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slots[0].room").value("EXAMINATION"))
			.andExpect(jsonPath("$.slots[0].from").value("10:00:00"))
			.andExpect(jsonPath("$.slots[0].through").value("11:00:00"));
	}

	@Test
	void scheduleShowsAllThreeClinicsAndPhysicalRoomStatesIndependentlyOfPolicies() throws Exception {
		this.mvc.perform(get("/rooms/schedule").param("date", DAY.toString()))
			.andExpect(status().isOk())
			.andExpect(view().name("rooms/schedule"))
			.andExpect(model().attribute("hours", hasSize(11)))
			.andExpect(model().attribute("clinics", List.of("north", "south", "centre")))
			.andExpect(content().string(containsString("Room 1 · Examination")))
			.andExpect(content().string(containsString("Room 2 · Surgery")))
			.andExpect(content().string(containsString("data-time=\"09:00\"")))
			.andExpect(content().string(containsString("slot-free")))
			.andExpect(content().string(containsString("slot-booked")))
			.andExpect(content().string(containsString("slot-maintenance")))
			.andExpect(content().string(containsString("slot-closed")));
		verifyNoInteractions(this.bookings);
	}

	@Test
	void schedulePreservesClinicDateAndContextPathWhenReturningToSearch() throws Exception {
		this.mvc
			.perform(get("/petclinic/rooms/schedule").contextPath("/petclinic")
				.param("clinic", "south")
				.param("date", "2026-10-02"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("clinic", "south"))
			.andExpect(model().attribute("date", DAY.plusDays(1)))
			.andExpect(content().string(containsString("action=\"/petclinic/rooms/schedule\"")))
			.andExpect(content().string(containsString("formaction=\"/petclinic/rooms\"")))
			.andExpect(content().string(not(containsString("ownerId"))));
	}

	@Test
	void successfulBookingRedirectsToTheSelectionWithAConfirmation() throws Exception {
		given(this.bookings.book("south", Room.EXAMINATION, DAY, LocalTime.of(10, 0))).willReturn(true);
		this.mvc
			.perform(post("/petclinic/rooms/book").contextPath("/petclinic")
				.param("clinic", "south")
				.param("room", "EXAMINATION")
				.param("date", DAY.toString())
				.param("time", "10:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/petclinic/rooms?clinic=south&date=2026-10-01"))
			.andExpect(flash().attribute("bookedSlot",
					new ClinicSchedule.AvailableSlot(Room.EXAMINATION, LocalTime.of(10, 0))));
		verify(this.bookings).book("south", Room.EXAMINATION, DAY, LocalTime.of(10, 0));
	}

	@Test
	void staleBookingRedirectsWithAUsefulMessage() throws Exception {
		this.mvc
			.perform(post("/rooms/book").param("clinic", "south")
				.param("room", "EXAMINATION")
				.param("date", DAY.toString())
				.param("time", "10:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("bookingUnavailable", true));
	}

	@Test
	void invalidSelectionsAreRejectedAndGetRequestsNeverBookARoom() throws Exception {
		for (String clinic : List.of("unknown", "east", "west", "training")) {
			this.mvc.perform(get("/rooms/schedule").param("clinic", clinic)).andExpect(status().isBadRequest());
			this.mvc.perform(get("/rooms").param("clinic", clinic)).andExpect(status().isBadRequest());
		}
		this.mvc.perform(get("/rooms/schedule").param("date", "2026-02-30")).andExpect(status().isBadRequest());
		this.mvc
			.perform(post("/rooms/book").param("clinic", "north")
				.param("room", "ANY")
				.param("date", DAY.toString())
				.param("time", "09:00"))
			.andExpect(status().isBadRequest());
		this.mvc
			.perform(post("/rooms/book").param("clinic", "north")
				.param("room", "EXAMINATION")
				.param("date", DAY.toString())
				.param("time", "garbage"))
			.andExpect(status().isBadRequest());
		this.mvc.perform(get("/rooms/book")).andExpect(status().isMethodNotAllowed());
		verifyNoInteractions(this.bookings, this.repository);
	}

	@Test
	void scheduleSupportsLocaleSelection() throws Exception {
		this.mvc.perform(get("/rooms/schedule").param("date", DAY.toString()).param("lang", "de"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Klinikplan")))
			.andExpect(content().string(containsString("Raum 1 · Untersuchung")))
			.andExpect(content().string(containsString("Geschlossen")));
	}

	@Test
	void scheduleStatusKeysAreIndependentOfTurkishCasing() throws Exception {
		this.mvc.perform(get("/rooms/schedule").param("date", DAY.toString()).param("lang", "tr"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("class=\"slot-maintenance\"")))
			.andExpect(content().string(containsString("Bakım")))
			.andExpect(content().string(not(containsString("??rooms."))));
	}

}
