package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class RoomsController {

	private final RoomBookingService bookings;

	private final ClinicSchedule schedule;

	RoomsController(RoomBookingService bookings, ClinicSchedule schedule) {
		this.bookings = bookings;
		this.schedule = schedule;
	}

	@GetMapping("/rooms")
	String rooms(@RequestParam(defaultValue = "north") String clinic,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Model model) {
		model.addAttribute("availability", availability(clinic, date));
		model.addAttribute("clinics", ClinicSchedule.CLINICS);
		return "rooms/availability";
	}

	@GetMapping("/rooms/schedule")
	String schedule(@RequestParam(defaultValue = "north") String clinic,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Model model) {
		validateClinic(clinic);
		LocalDate day = date == null ? LocalDate.now().plusDays(1) : date;
		model.addAttribute("clinic", clinic);
		model.addAttribute("date", day);
		model.addAttribute("clinics", ClinicSchedule.CLINICS);
		model.addAttribute("rooms", Room.values());
		model.addAttribute("hours", this.schedule.hours(day));
		model.addAttribute("openingTime", this.schedule.openingTime());
		model.addAttribute("closingTime", this.schedule.closingTime());
		return "rooms/schedule";
	}

	@GetMapping("/rooms/availability")
	@ResponseBody
	Availability availability(@RequestParam(defaultValue = "north") String clinic,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		validateClinic(clinic);
		LocalDate day = date == null ? LocalDate.now().plusDays(1) : date;
		return new Availability(clinic, day, this.bookings.available(clinic, day));
	}

	@PostMapping("/rooms/book")
	String book(@RequestParam String clinic, @RequestParam Room room,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time, RedirectAttributes redirect) {
		validateClinic(clinic);
		if (this.bookings.book(clinic, room, date, time)) {
			redirect.addFlashAttribute("bookedSlot", new ClinicSchedule.AvailableSlot(room, time));
		}
		else {
			redirect.addFlashAttribute("bookingUnavailable", true);
		}
		redirect.addAttribute("clinic", clinic);
		redirect.addAttribute("date", date.toString());
		return "redirect:/rooms";
	}

	private void validateClinic(String clinic) {
		if (!ClinicSchedule.CLINICS.contains(clinic)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a clinic");
		}
	}

	record Availability(String clinic, LocalDate date, List<ClinicSchedule.AvailableSlot> slots) {
	}

}
